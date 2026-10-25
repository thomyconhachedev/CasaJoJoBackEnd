package com.casajojoapp.backend.controller;

import com.casajojoapp.backend.dto.request.LoginRequest;
import com.casajojoapp.backend.dto.request.RegistroRequest;
import com.casajojoapp.backend.dto.response.AuthResponse;
import com.casajojoapp.backend.entity.Usuario;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrarCliente(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(usuarioService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> perfil(Authentication authentication,
                                               @RequestHeader("Authorization") String authorization) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            throw BusinessRuleException.noAutenticado("Debes iniciar sesión para ver tu perfil.");
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : authorization;
        return ResponseEntity.ok(usuarioService.obtenerPerfil(usuario.getId(), token));
    }
}
