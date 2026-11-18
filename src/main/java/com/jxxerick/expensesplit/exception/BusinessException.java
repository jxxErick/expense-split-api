package com.jxxerick.expensesplit.exception;

/** Request is well-formed but breaks a business rule. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
