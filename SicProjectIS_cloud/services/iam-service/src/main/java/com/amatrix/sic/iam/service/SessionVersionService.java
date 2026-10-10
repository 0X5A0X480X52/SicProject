package com.amatrix.sic.iam.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import com.amatrix.sic.iam.dao.SessionMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Redis fences deny access across DB commit/publication; they deliberately have no expiry. */
@Service
public class SessionVersionService {
    private static final Logger log = LoggerFactory.getLogger(SessionVersionService.class);
    private static final String PREFIX = "sic:jwt:pending:";
    private static final int BATCH_SIZE=100;
    private static final long PUBLICATION_BUDGET_NANOS=java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    static final DefaultRedisScript<Long> RESERVE_BATCH=new DefaultRedisScript<>("""
            for i,key in ipairs(KEYS) do if redis.call('EXISTS',key) == 1 then return 0 end end
            for i,key in ipairs(KEYS) do redis.call('SET',key,ARGV[1]) end
            return 1
            """,Long.class);
    static final DefaultRedisScript<Long> PUBLISH_BATCH=new DefaultRedisScript<>("""
            for i=1,#KEYS,2 do
              if redis.call('GET',KEYS[i+1]) ~= ARGV[1] then return 0 end
              local current=redis.call('GET',KEYS[i])
              if current and tonumber(current) > tonumber(ARGV[(i+1)/2+1]) then return -1 end
            end
            for i=1,#KEYS,2 do
              redis.call('SET',KEYS[i],ARGV[(i+1)/2+1]); redis.call('DEL',KEYS[i+1])
            end
            return 1
            """,Long.class);
    private static final DefaultRedisScript<Long> RELEASE_BATCH=new DefaultRedisScript<>("""
            local released=0
            for i,key in ipairs(KEYS) do
              if redis.call('GET',key) == ARGV[1] then released=released+redis.call('DEL',key) end
            end
            return released
            """,Long.class);
    static final DefaultRedisScript<Long> LOGIN = new DefaultRedisScript<>("""
            if redis.call('EXISTS',KEYS[2]) == 1 then return -2 end
            local current=redis.call('GET',KEYS[1])
            if current and tonumber(current) > tonumber(ARGV[1]) then return -1 end
            redis.call('SET',KEYS[1],ARGV[1]); return 1
            """, Long.class);
    static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[2]) ~= ARGV[2] then return 0 end
            local current=redis.call('GET',KEYS[1])
            if current and tonumber(current) > tonumber(ARGV[1]) then return -1 end
            redis.call('SET',KEYS[1],ARGV[1]); redis.call('DEL',KEYS[2]); return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final SessionMapper mapper;
    private final TransactionTemplate transactions;

    public SessionVersionService(StringRedisTemplate redis, SessionMapper mapper, PlatformTransactionManager manager) {
        this.redis = redis;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(5);
    }

    public void publishLogin(Long userId, long version) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            publishAfterCommit(userId, version);
            return;
        }
        Long result;
        try { result = redis.execute(LOGIN, keys(userId), Long.toString(version)); }
        catch (RuntimeException ex) { throw unavailable(ex); }
        if (Long.valueOf(-1).equals(result))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Authorization changed during login; retry login");
        if (!Long.valueOf(1).equals(result)) throw unavailable(null);
    }

    public void publishAfterCommit(Long userId, long version) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Authorization version updates require a database transaction");
        Pending pending = TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(Pending.class::isInstance).map(Pending.class::cast).findFirst().orElse(null);
        if (pending == null) {
            pending = new Pending();
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.versions.merge(userId,version,Math::max);
    }

    private final class Pending implements TransactionSynchronization {
        private final String owner = UUID.randomUUID().toString();
        private final Map<Long,Long> versions = new LinkedHashMap<>();
        private List<Long> ordered() { return versions.keySet().stream().sorted().toList(); }

        @Override public void beforeCommit(boolean readOnly) {
            long started=System.nanoTime();
            var ids=ordered();
            for (int offset=0;offset<ids.size();offset+=BATCH_SIZE) {
                checkBudget(started);
                var chunk=ids.subList(offset,Math.min(ids.size(),offset+BATCH_SIZE));
                try {
                    boolean success=chunk.size()==1
                            ? Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX+chunk.getFirst(),owner))
                            : Long.valueOf(1).equals(redis.execute(RESERVE_BATCH,chunk.stream().map(id -> PREFIX+id).toList(),owner));
                    if (!success)
                        throw unavailable(null);
                } catch (RuntimeException ex) { throw unavailable(ex); }
            }
        }

        @Override public void afterCommit() {
            long started=System.nanoTime();
            var ids=ordered();
            for(int offset=0;offset<ids.size();offset+=BATCH_SIZE) {
                checkBudget(started);
                var chunk=ids.subList(offset,Math.min(ids.size(),offset+BATCH_SIZE));
                if(chunk.size()==1) { publish(chunk.getFirst(),versions.get(chunk.getFirst()),owner);continue; }
                var keys=new java.util.ArrayList<String>();var args=new java.util.ArrayList<String>();args.add(owner);
                chunk.forEach(id -> { keys.addAll(keys(id));args.add(Long.toString(versions.get(id))); });
                try {
                    if(!Long.valueOf(1).equals(redis.execute(PUBLISH_BATCH,keys,args.toArray())))throw unavailable(null);
                } catch(RuntimeException ex) { throw unavailable(ex); }
            }
        }

        @Override public void afterCompletion(int status) {
            if (status != STATUS_ROLLED_BACK) return; // Unknown commit remains fenced until DB recovery.
            long started=System.nanoTime();
            var ids=ordered();
            for(int offset=0;offset<ids.size();offset+=BATCH_SIZE) {
                if(System.nanoTime()-started>PUBLICATION_BUDGET_NANOS)break;
                // Include commands whose response was lost: only this transaction's owner may release a fence.
                var chunk=ids.subList(offset,Math.min(ids.size(),offset+BATCH_SIZE));
                try { redis.execute(RELEASE_BATCH,chunk.stream().map(id -> PREFIX+id).toList(),owner); }
                catch (RuntimeException ex) { log.warn("Session fence recovery required for {} users",ids.size());break; }
            }
        }
    }

    private static void checkBudget(long started) {
        if(System.nanoTime()-started>PUBLICATION_BUDGET_NANOS)throw unavailable(null);
    }

    private void publish(Long userId, long version, String owner) {
        try {
            if (!Long.valueOf(1).equals(redis.execute(PUBLISH, keys(userId),Long.toString(version),owner)))
                throw unavailable(null);
        } catch (RuntimeException ex) { throw unavailable(ex); }
    }

    /** Locking read waits for an interrupted writer's commit/rollback before clearing its fence. */
    @Scheduled(initialDelayString="${sic.security.session-recovery-delay-ms:10000}",
            fixedDelayString="${sic.security.session-recovery-delay-ms:10000}")
    public void recover() {
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(100).build())) {
            long started=System.nanoTime();
            int processed = 0;
            while (cursor.hasNext() && processed++ < 100) {
                if(System.nanoTime()-started>PUBLICATION_BUDGET_NANOS)break;
                String key = cursor.next();
                Long userId = Long.valueOf(key.substring(PREFIX.length()));
                transactions.executeWithoutResult(status -> {
                    List<Long> versions = mapper.lockedVersion(userId);
                    String owner = redis.opsForValue().get(key);
                    if (owner == null) return;
                    if (versions.isEmpty()) redis.execute(RELEASE,List.of(key),owner);
                    else publish(userId,versions.getFirst(),owner);
                });
            }
        } catch (RuntimeException ex) {
            log.warn("Session version recovery unavailable ({})",ex.getClass().getSimpleName());
        }
    }

    private static List<String> keys(Long id) { return List.of("sic:jwt:version:" + id,PREFIX + id); }
    private static ResponseStatusException unavailable(Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Session authorization is temporarily unavailable",cause);
    }
}
