package com.amatrix.sic.messaging;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * BeanPostProcessor 包装 DataSource 及其连接/语句，按 acquire/prepare/statement/hold 阶段计时并记录超阈值告警；只注入校验通过的 traceId。
 */
public final class JdbcLatencyObserver implements BeanPostProcessor,org.springframework.beans.factory.SmartInitializingSingleton {
    private static final org.slf4j.Logger log=LoggerFactory.getLogger(JdbcLatencyObserver.class);
    private final ObjectProvider<MeterRegistry> registries;
    private volatile MeterRegistry registry;
    private final long acquireMs,queryMs,holdMs;
    public JdbcLatencyObserver(Environment environment,ObjectProvider<MeterRegistry> registries) {
        this.registries=registries;
        acquireMs=environment.getProperty("sic.observability.jdbc.acquire-warning-ms",Long.class,100L);
        queryMs=environment.getProperty("sic.observability.jdbc.query-warning-ms",Long.class,200L);
        holdMs=environment.getProperty("sic.observability.jdbc.hold-warning-ms",Long.class,1000L);
    }

    @Override public void afterSingletonsInstantiated() {
        try { registry=registries.getIfUnique(); }
        catch(RuntimeException ex) { log.warn("JDBC metrics unavailable ({})",ex.getClass().getSimpleName()); }
    }

    @Override public Object postProcessAfterInitialization(Object bean,String name) {
        return bean instanceof DataSource source && !(source instanceof ObservedSource)
                ? new ObservedSource(source):bean;
    }

    private final class ObservedSource extends DelegatingDataSource implements AutoCloseable {
        ObservedSource(DataSource target) { super(target); }
        @Override public Connection getConnection() throws SQLException { return acquire(super::getConnection); }
        @Override public Connection getConnection(String user,String password) throws SQLException {
            return acquire(() -> super.getConnection(user,password));
        }
        private Connection acquire(ConnectionSupplier supplier) throws SQLException {
            long start=System.nanoTime();boolean success=false;
            try { var connection=connection(supplier.get());success=true;return connection; }
            finally { record("acquire","connection",start,success,acquireMs,null); }
        }
        @Override public void close() throws Exception {
            if(getTargetDataSource() instanceof AutoCloseable target)target.close();
        }
    }

    private Connection connection(Connection target) {
        long acquired=System.nanoTime();
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
            Object[] passed=args;
            String sql=null;
            if((method.getName().equals("prepareStatement") || method.getName().equals("prepareCall"))
                    && args!=null && args.length>0 && args[0] instanceof String text) {
                sql=text;passed=args.clone();passed[0]=traced(text);
            }
            long started=System.nanoTime();
            boolean success=false;
            try {
                Object result=invoke(target,method,passed);
                success=true;
                if(result instanceof Statement statement)return statement(statement,sql);
                return result;
            } finally {
                if(sql!=null)record("prepare",operation(sql),started,success,queryMs,sql);
                if(method.getName().equals("close"))record("hold","connection",acquired,success,holdMs,null);
            }
        });
    }

    private Statement statement(Statement target,String preparedSql) {
        Class<?> contract=target instanceof CallableStatement?CallableStatement.class:
                target instanceof PreparedStatement?PreparedStatement.class:Statement.class;
        return (Statement)Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{contract},(proxy,method,args) -> {
            boolean executes=method.getName().startsWith("execute");
            String sql=preparedSql;
            Object[] passed=args;
            if(executes && args!=null && args.length>0 && args[0] instanceof String text) {
                sql=text;passed=args.clone();passed[0]=traced(text);
            }
            if(!executes)return invoke(target,method,args);
            long start=System.nanoTime();boolean success=false;
            try { Object result=invoke(target,method,passed);success=true;return result; }
            finally { record("statement",operation(sql),start,success,queryMs,sql); }
        });
    }

    private void record(String phase,String operation,long started,boolean success,long warningMs,String sql) {
        long elapsed=System.nanoTime()-started;
        try {
            if(registry!=null)registry.timer("sic.jdbc.duration","phase",phase,"operation",operation,
                    "outcome",success?"success":"error").record(elapsed,java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch(RuntimeException ex) { log.debug("JDBC metric recording failed ({})",ex.getClass().getSimpleName()); }
        if(elapsed/1_000_000>=warningMs || !success)
            log.warn("JDBC phase={} operation={} elapsedMs={} outcome={} sqlHash={}",phase,operation,
                    elapsed/1_000_000,success?"success":"error",sql==null?"-":Integer.toUnsignedString(sql.hashCode(),16));
    }

    private static String traced(String sql) {
        String trace=MDC.get("traceId");
        return trace!=null && trace.matches("[A-Za-z0-9-]{1,96}")?"/* sic_trace="+trace+" */ "+sql:sql;
    }
    private static String operation(String sql) {
        if(sql==null)return "batch";
        String verb=sql.stripLeading().split("\\s+",2)[0].toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("select","insert","update","delete","create","alter").contains(verb)?verb:"other";
    }
    private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
        try { return method.invoke(target,args); }
        catch(InvocationTargetException ex) { throw ex.getCause(); }
    }
    @FunctionalInterface private interface ConnectionSupplier { Connection get() throws SQLException; }
}
