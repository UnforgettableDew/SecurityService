package com.crossuniversity.securityservice.exception.bad_request;

public class DocumentBadRequestException extends RuntimeException{
    public DocumentBadRequestException(String message) {
        super(message);
    }
}
