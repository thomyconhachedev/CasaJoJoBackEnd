package com.casajojoapp.backend.exception;

/**
 * Error de negocio con su codigo HTTP:
 * 400 datos mal formados, 409 choca con el estado actual (stock, transicion), 422 viola una regla del negocio.
 */
public class BusinessRuleException extends RuntimeException {

    private final int status;

    private BusinessRuleException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessRuleException invalida(String mensaje) {
        return new BusinessRuleException(400, mensaje);
    }

    public static BusinessRuleException conflicto(String mensaje) {
        return new BusinessRuleException(409, mensaje);
    }

    public static BusinessRuleException reglaNegocio(String mensaje) {
        return new BusinessRuleException(422, mensaje);
    }
}
