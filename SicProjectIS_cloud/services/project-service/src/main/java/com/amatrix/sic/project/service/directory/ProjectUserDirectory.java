package com.amatrix.sic.project.service.directory;

import com.amatrix.sic.project.client.IamDirectoryClient;
import com.amatrix.sic.project.client.auth.InternalTokenProvider;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;

import com.amatrix.sic.project.dao.UserSnapshotMapper;
import com.amatrix.sic.project.entity.UserSnapshotRow;
import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.UserSummary;

import tools.jackson.databind.ObjectMapper;

/**
 * 本地 user_snapshot 目录查询；require 在缺少快照时从 IAM 拉取存在用户，refresh 强制拉取、upsert 更新，allEnabled 提供启用候选用户；require 本身不拒绝禁用用户。
 * 页面候选列表有短缓存；find/requireAll 直接读取本地快照，只有缺失补齐或显式 refresh/fetchAll 才访问 IAM。
 * 本地快照可能短暂落后于 IAM 身份，不能把不使用页面缓存解释成实时读取 IAM。
 */
@Component
public class ProjectUserDirectory {
    private final UserSnapshotMapper mapper;
    private final IamDirectoryClient client;
    private final InternalTokenProvider tokens;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.atomic.AtomicLong selectorEpoch=new java.util.concurrent.atomic.AtomicLong();
    private volatile SelectorSnapshot selectorSnapshot;
    private record SelectorSnapshot(long epoch,long expiresAt,List<UserSummary> users) {}

    public ProjectUserDirectory(UserSnapshotMapper mapper, IamDirectoryClient client, InternalTokenProvider tokens,
            ObjectMapper objectMapper) {
        this.mapper = mapper; this.client = client; this.tokens = tokens; this.objectMapper = objectMapper;
    }

    public UserSummary require(Long userId) {
        UserSummary snapshot = find(userId);
        if (snapshot != null) return snapshot;
        var response = client.summary(tokens.authorization(), userId);
        if (!response.success() || response.data() == null) throw new IllegalArgumentException("Selected user does not exist");
        upsert(response.data());
        return response.data();
    }

    /**
     * 优先批量读取本地用户快照，仅对缺失用户调用 IAM 并补齐快照；该方法不保证已有快照与 IAM 实时一致。
     */
    public Map<Long, UserSummary> requireAll(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, UserSummary> result = new LinkedHashMap<>();
        for (int offset = 0; offset < ids.size(); offset += 100) {
            List<Long> chunk = ids.subList(offset, Math.min(ids.size(), offset + 100));
            mapper.users(chunk).forEach(row -> result.put(row.userId(),summary(row)));
            List<Long> missing = chunk.stream().filter(id -> !result.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                for (UserSummary user : fetchAll(missing)) { upsert(user); result.put(user.userId(), user); }
            }
        }
        return result;
    }

    /**
     * 直接分批读取 IAM 的用户摘要并校验返回 ID 集合完整性，不使用页面候选缓存，也不在本方法内写入本地快照。
     */
    public List<UserSummary> fetchAll(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        java.util.ArrayList<UserSummary> result = new java.util.ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += 100) {
            List<Long> chunk = ids.subList(offset, Math.min(ids.size(), offset + 100));
            var response = client.summaries(tokens.authorization(), chunk);
            if (!response.success() || response.data() == null
                    || !response.data().stream().map(UserSummary::userId).collect(java.util.stream.Collectors.toSet())
                            .equals(new java.util.HashSet<>(chunk))) {
                throw new IllegalStateException("IAM user summaries are incomplete");
            }
            result.addAll(response.data());
        }
        return result;
    }

    public UserSummary refresh(Long userId) {
        var response = client.summary(tokens.authorization(), userId);
        if (!response.success() || response.data() == null) {
            throw new IllegalStateException("IAM user summary is unavailable: " + userId);
        }
        upsert(response.data());
        return response.data();
    }

    public UserSummary find(Long userId) {
        var row=mapper.user(userId);
        return row==null?null:summary(row);
    }

    /**
     * 提供 UI 候选列表的两秒缓存。find/requireAll 仍读本地快照；只有显式 refresh、fetchAll 或缺失补齐才访问 IAM。
     */
    public List<UserSummary> allEnabled() {
        long epoch=selectorEpoch.get(),now=System.nanoTime();
        SelectorSnapshot cached=selectorSnapshot;
        if(cached!=null&&cached.epoch()==epoch&&now<cached.expiresAt()
                &&!org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(this))return cached.users();
        List<UserSummary> users=mapper.enabled().stream().map(this::summary).toList();
        // 事务尚未提交的快照不能发布为其他请求可见的候选缓存。
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                &&selectorEpoch.get()==epoch)
            selectorSnapshot=new SelectorSnapshot(epoch,now+java.util.concurrent.TimeUnit.SECONDS.toNanos(2),users);
        return users;
    }

    /**
     * 写入用户快照；事务内更新在提交后使候选缓存失效，避免其他请求看到尚未提交的缓存内容。
     */
    public void upsert(UserSummary user) {
        mapper.upsert(user,write(user.roleCodes()));
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            if(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(this))return;
            org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(this,Boolean.TRUE);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void suspend() {
                        org.springframework.transaction.support.TransactionSynchronizationManager.unbindResourceIfPossible(ProjectUserDirectory.this);
                    }
                    @Override public void resume() {
                        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(ProjectUserDirectory.this,Boolean.TRUE);
                    }
                    @Override public void afterCommit() { invalidateSelector(); }
                    @Override public void afterCompletion(int status) {
                        org.springframework.transaction.support.TransactionSynchronizationManager.unbindResourceIfPossible(ProjectUserDirectory.this);
                    }
                });
        } else invalidateSelector();
    }

    private void invalidateSelector() { selectorEpoch.incrementAndGet();selectorSnapshot=null; }

    private UserSummary summary(UserSnapshotRow row) {
        return new UserSummary(row.userId(),row.username(),row.realName(),row.deptId(),row.deptName(),
                readRoles(row.rolesJson()),row.enabled(),row.version());
    }

    @SuppressWarnings("unchecked")
    private List<String> readRoles(String json) {
        try { return List.copyOf(objectMapper.readValue(json, List.class)); }
        catch (Exception ex) { return List.of(); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
