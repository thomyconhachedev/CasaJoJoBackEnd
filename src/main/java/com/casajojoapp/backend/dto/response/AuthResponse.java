package com.casajojoapp.backend.dto.response;

/** Nunca incluye la contraseña, ni siquiera el hash. */
public record AuthResponse(
        String token,
        Long id,
        String username,
        String rol,
        String nombre,
        String telefono) {
}
