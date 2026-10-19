package com.example.CasaJoJoBackEnd.exception;

public class SincronizacionException extends RuntimeException {

    public SincronizacionException(String message) {
        super(message);
    }

    public SincronizacionException(String message, Throwable cause) {
        super(message, cause);
    }
}
