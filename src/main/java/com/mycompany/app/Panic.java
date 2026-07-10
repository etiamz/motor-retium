package com.mycompany.app;

@SuppressWarnings("serial")
public final class Panic extends RuntimeException {
    public Panic(final String message) {
        super(message);
    }
}
