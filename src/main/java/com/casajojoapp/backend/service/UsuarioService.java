package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.request.LoginRequest;
import com.casajojoapp.backend.dto.request.RegistroRequest;
import com.casajojoapp.backend.dto.response.AuthResponse;

public interface UsuarioService {

    /** El registro publico siempre crea Rol.USUARIO: nadie se puede auto-promover a admin. */
    AuthResponse registrarCliente(RegistroRequest request);

    AuthResponse login(LoginRequest request);

    /** Datos del usuario de la sesion actual; devuelve el mismo token con el que consulto. */
    AuthResponse obtenerPerfil(Long id, String token);
}
