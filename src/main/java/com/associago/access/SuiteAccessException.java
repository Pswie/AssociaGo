package com.associago.access;

public class SuiteAccessException extends RuntimeException {
    private final int status;
    private final String code;
    public SuiteAccessException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
