package com.casajojoapp.backend.enums;

import com.casajojoapp.backend.exception.BusinessRuleException;

import java.util.Arrays;
import java.util.Locale;

/** Ciclo de vida: RECIBIDO -> EN_PREPARACION -> LISTO_PARA_RETIRO -> ENTREGADO. */
public enum EstadoPedido {
    RECIBIDO,
    EN_PREPARACION,
    LISTO_PARA_RETIRO,
    ENTREGADO;

    /** Unico estado al que puede pasar desde este (null si ya es el ultimo). */
    public EstadoPedido siguiente() {
        return switch (this) {
            case RECIBIDO -> EN_PREPARACION;
            case EN_PREPARACION -> LISTO_PARA_RETIRO;
            case LISTO_PARA_RETIRO -> ENTREGADO;
            case ENTREGADO -> null;
        };
    }

    public static EstadoPedido desde(String valor) {
        String validos = Arrays.toString(values());
        if (valor == null || valor.isBlank()) {
            throw BusinessRuleException.invalida("El estado es obligatorio. Valores válidos: " + validos + ".");
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BusinessRuleException.invalida(
                    "Estado no válido: \"" + valor + "\". Valores válidos: " + validos + ".");
        }
    }
}
