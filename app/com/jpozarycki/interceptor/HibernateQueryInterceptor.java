package com.jpozarycki.interceptor;

import com.jpozarycki.exceptions.NPlusOneQueriesException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Transaction;
import org.hibernate.proxy.HibernateProxy;

import javax.inject.Singleton;
import java.io.Serializable;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class HibernateQueryInterceptor extends EmptyInterceptor {
    private final transient ThreadLocal<Long> threadQueryCount = new ThreadLocal<>();
    private final transient ThreadLocal<Set<String>> threadPreviouslyLoadedEntities =
            ThreadLocal.withInitial(new EmptySetSupplier());
    private final transient ThreadLocal<Map<String, String>> threadProxyMethodEntityMapping =
            ThreadLocal.withInitial(new EmptyMapSupplier());
    private static final String HIBERNATE_PROXY_PREFIX = "org.hibernate.proxy";
    private static final String PROXY_METHOD_PREFIX = "com.sun.proxy";

    private final HibernateQueryInterceptorProperties hibernateQueryInterceptorProperties;

    /**
     * Start or reset the query count to 0 for the considered thread
     */
    public void startQueryCount() {
        threadQueryCount.set(0L);
    }

    /**
     * Get the query count for the considered thread
     */
    public Long getQueryCount() {
        return threadQueryCount.get();
    }

    /**
     * Increment the query count for the considered thread for each new statement if the count has been initialized
     *
     * @param sql Query to be executed
     * @return Query to be executed
     */
    @Override
    public String onPrepareStatement(String sql) {
        Long count = threadQueryCount.get();
        if (count != null) {
            threadQueryCount.set(count + 1);
        }
        return super.onPrepareStatement(sql);
    }

    /**
     * Reset previously loaded entities after the end of a transaction to avoid triggering
     * N+1 queries exceptions because of loading same instance in two different transactions
     *
     * @param tx Transaction having been completed
     */
    @Override
    public void afterTransactionCompletion(Transaction tx) {
        threadPreviouslyLoadedEntities.set(new HashSet<>());
        threadProxyMethodEntityMapping.set(new HashMap<>());
    }

    /**
     * Detect the N+1 queries by checking if two calls were made to getEntity for the same instance
     * <p>
     * The first call is made with the instance filled with a {@link HibernateProxy}
     * and the second is made after a query was executed to fetch the data in the Entity
     *
     * @param entityName Name of the entity to get
     * @param id         Id of the entity to get
     */
    @Override
    public Object getEntity(String entityName, Serializable id) {
        detectNPlusOneQueriesOfMissingQueryEagerFetching(entityName, id);

        detectNPlusOneQueriesOfMissingEntityFieldLazyFetching(entityName, id);

        Set<String> previouslyLoadedEntities = threadPreviouslyLoadedEntities.get();

        if (previouslyLoadedEntities.contains(entityName + id)) {
            previouslyLoadedEntities.remove(entityName + id);
            threadPreviouslyLoadedEntities.set(previouslyLoadedEntities);
        } else {
            previouslyLoadedEntities.add(entityName + id);
            threadPreviouslyLoadedEntities.set(previouslyLoadedEntities);
        }

        return null;
    }

    /**
     * Detect the N+1 queries caused by a missing eager fetching configuration on a query with a lazy loaded field
     * <p>
     * <p>
     * Detection checks:
     * - The getEntity was called twice for the couple (entity, id)
     * <p>
     * - There is an occurrence of hibernate proxy followed by entity class in the stackTraceElements
     * Avoid detecting calls to queries like findById and queries with eager fetching on some entity fields
     *
     * @param entityName Name of the entity
     * @param id         Id of the entity objecy
     * @return Boolean telling whether N+1 queries were detected or not
     */
    private boolean detectNPlusOneQueriesOfMissingQueryEagerFetching(String entityName, Serializable id) {
        Set<String> previouslyLoadedEntities = threadPreviouslyLoadedEntities.get();

        if (!previouslyLoadedEntities.contains(entityName + id)) {
            return false;
        }

        // Detect N+1 queries by searching for newest occurrence of Hibernate proxy followed by entity class in stack
        // elements
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StackTraceElement originStackTraceElement = null;

        for (int i = 0; i < stackTraceElements.length - 3; i++) {
            if (
                    stackTraceElements[i].getClassName().indexOf(HIBERNATE_PROXY_PREFIX) == 0
                            && stackTraceElements[i + 1].getClassName().indexOf(entityName) == 0
            ) {
                originStackTraceElement = stackTraceElements[i + 2];
                break;
            }
        }

        if (originStackTraceElement == null) {
            return false;
        }

        String errorMessage = "N+1 queries detected on a getter of the entity " + entityName +
                "\n    at " + originStackTraceElement.toString() +
                "\n    Hint: Missing Eager fetching configuration on the query that fetched the object of " +
                "type " + entityName + "\n";
        logDetectedNPlusOneQueries(errorMessage);

        return true;
    }

    /**
     * Detect the N+1 queries caused by a missing lazy fetching configuration on an entity field
     * <p>
     * Detection checks:
     * - The getEntity was called twice for the couple (entity, id)
     * <p>
     * - The query that triggered the fetching of the entity object was first called for a different entity
     * Avoid detecting calls to queries like findById
     *
     * @param entityName Name of the entity
     * @param id         Id of the entity objecy
     * @return Boolean telling whether N+1 queries were detected or not
     */
    private boolean detectNPlusOneQueriesOfMissingEntityFieldLazyFetching(String entityName, Serializable id) {
        Optional<String> optionalProxyMethodName = getProxyMethodName();
        if (!optionalProxyMethodName.isPresent()) {
            return false;
        }
        String proxyMethodName = optionalProxyMethodName.get();

        Set<String> previouslyLoadedEntities = threadPreviouslyLoadedEntities.get();
        Map<String, String> proxyMethodEntityMapping = threadProxyMethodEntityMapping.get();

        boolean nPlusOneQueriesDetected = false;
        if (
                previouslyLoadedEntities.contains(entityName + id)
                        && proxyMethodEntityMapping.containsKey(proxyMethodName)
                        && !proxyMethodEntityMapping.get(proxyMethodName).equals(entityName)
        ) {
            nPlusOneQueriesDetected = true;

            String errorMessage = "N+1 queries detected on a query for the entity " + entityName;

            // Find origin of the N+1 queries in client package
            // by getting oldest occurrence of proxy method in stack elements
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

            for (int i = stackTraceElements.length - 1; i >= 1; i--) {
                if (stackTraceElements[i - 1].getClassName().indexOf(PROXY_METHOD_PREFIX) == 0) {
                    errorMessage += "\n    at " + stackTraceElements[i].toString();
                    break;
                }
            }

            errorMessage += "\n    Hint: Missing Lazy fetching configuration on a field of one of the entities " +
                    "fetched in the query\n";

            logDetectedNPlusOneQueries(errorMessage);
        }

        proxyMethodEntityMapping.putIfAbsent(proxyMethodName, entityName);
        return nPlusOneQueriesDetected;
    }

    /**
     * Get the Proxy method name that was called first to know which query triggered the interceptor
     *
     * @return Optional of method name if found
     */
    private Optional<String> getProxyMethodName() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        for (int i = stackTraceElements.length - 1; i >= 0; i--) {
            StackTraceElement stackTraceElement = stackTraceElements[i];

            if (stackTraceElement.getClassName().indexOf("com.sun.proxy") == 0) {
                return Optional.of(stackTraceElement.getClassName() + stackTraceElement.getMethodName());
            }
        }

        return Optional.empty();
    }

    /**
     * Log the detected N+1 queries error message or throw an exception depending on the configured error level
     *
     * @param errorMessage Error message for the N+1 queries detected
     */
    private void logDetectedNPlusOneQueries(String errorMessage) {
        switch (hibernateQueryInterceptorProperties.getErrorLevel()) {
            case INFO:
                log.info(errorMessage);
                break;
            case WARN:
                log.warn(errorMessage);
                break;
            case ERROR:
                log.error(errorMessage);
                break;
            default:
                throw new NPlusOneQueriesException(errorMessage);
        }
    }
}

class EmptySetSupplier implements Supplier<Set<String>> {
    public Set<String> get() {
        return new HashSet<>();
    }
}

class EmptyMapSupplier implements Supplier<Map<String, String>> {
    public Map<String, String> get() {
        return new HashMap<>();
    }
}
