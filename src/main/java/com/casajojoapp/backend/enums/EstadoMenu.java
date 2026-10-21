package com.casajojoapp.backend.enums;

import com.casajojoapp.backend.exception.BusinessRuleException;

import java.util.Arrays;
import java.util.Locale;

public enum EstadoMenu {
    ABIERTO,
    CERRADO;

    public static EstadoMenu desde(String valor) {
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
