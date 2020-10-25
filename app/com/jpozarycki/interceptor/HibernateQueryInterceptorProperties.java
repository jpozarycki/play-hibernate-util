package com.jpozarycki.interceptor;

public class HibernateQueryInterceptorProperties {
    enum ErrorLevel {
        INFO,
        WARN,
        ERROR,
        EXCEPTION
    }

    /**
     * Error level for the N+1 queries detection.
     */
    private ErrorLevel errorLevel = ErrorLevel.ERROR;

    public ErrorLevel getErrorLevel() {
        return errorLevel;
    }

    public void setErrorLevel(String errorLevel) {
        this.errorLevel = ErrorLevel.valueOf(errorLevel);
    }
}
