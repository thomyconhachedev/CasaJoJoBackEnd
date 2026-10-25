package com.casajojoapp.backend.service.impl;

import com.casajojoapp.backend.dto.request.LoginRequest;
import com.casajojoapp.backend.dto.request.RegistroRequest;
import com.casajojoapp.backend.dto.response.AuthResponse;
import com.casajojoapp.backend.entity.Usuario;
import com.casajojoapp.backend.enums.Rol;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.exception.ResourceNotFoundException;
import com.casajojoapp.backend.repository.UsuarioRepository;
import com.casajojoapp.backend.security.JwtService;
import com.casajojoapp.backend.service.UsuarioService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    /** Mensaje unico para usuario inexistente y clave mala: no revela si la cuenta existe. */
    private static final String CREDENCIALES_INCORRECTAS = "Usuario o contraseña incorrectos.";
    private static final int MAX_BYTES_BCRYPT = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuthResponse registrarCliente(RegistroRequest request) {
        String username = normalizar(request.username());
        if (username.length() < 3 || username.length() > 50) {
            throw BusinessRuleException.invalida("El nombre de usuario debe tener entre 3 y 50 caracteres.");
        }

        String password = request.password();
        if (password == null || password.length() < 6) {
            throw BusinessRuleException.invalida("La contraseña debe tener al menos 6 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
            throw BusinessRuleException.invalida("La contraseña es demasiado larga.");
        }

        if (usuarioRepository.existsByUsername(username)) {
            throw BusinessRuleException.conflicto("El nombre de usuario ya está en uso.");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(Rol.USUARIO);
        usuario.setNombre(limpiar(request.nombre()));
        usuario.setTelefono(limpiar(request.telefono()));
        usuario.setCreadoEn(LocalDateTime.now(clock));

        try {
            usuario = usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultaneos con el mismo nombre: gana el primero.
            throw BusinessRuleException.conflicto("El nombre de usuario ya está en uso.");
        }
        return aResponse(usuario, jwtService.generarToken(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(normalizar(request.username())).orElse(null);
        String password = request.password();

        if (usuario == null || password == null || !passwordEncoder.matches(password, usuario.getPassword())) {
            throw BusinessRuleException.noAutenticado(CREDENCIALES_INCORRECTAS);
        }
        return aResponse(usuario, jwtService.generarToken(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse obtenerPerfil(Long id, String token) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario de esta sesión."));
        return aResponse(usuario, token);
    }

    /** Los nombres de usuario no distinguen mayusculas: "Ana" y "ana" son la misma cuenta. */
    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String limpiar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private AuthResponse aResponse(Usuario u, String token) {
        return new AuthResponse(token, u.getId(), u.getUsername(), u.getRol().name(), u.getNombre(), u.getTelefono());
    }
}
