package com.app.categorise.exception;

public class SubcategoryNotFoundException extends RuntimeException {
    public SubcategoryNotFoundException(String message) {
        super(message);
    }
}
