package com.jpozarycki.exceptions;

import org.hibernate.CallbackException;

public class NPlusOneQueriesException extends CallbackException {
    public NPlusOneQueriesException(String message) {
        super(message);
    }
}
