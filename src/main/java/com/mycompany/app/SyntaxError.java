package com.mycompany.app;

@SuppressWarnings("serial")
public final class SyntaxError extends RuntimeException {
    public SyntaxError(final String message) {
        super(message);
    }
}
