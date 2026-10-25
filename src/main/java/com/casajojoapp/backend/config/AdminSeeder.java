package com.casajojoapp.backend.config;

import com.casajojoapp.backend.entity.Usuario;
import com.casajojoapp.backend.enums.Rol;
import com.casajojoapp.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Crea la cuenta admin la primera vez que arranca. Si ya existe no la toca (ni cambia su clave). */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String CLAVE_DE_EJEMPLO = "casajojo2026";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String username;
    private final String password;

    public AdminSeeder(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       Clock clock,
                       @Value("${app.admin.username}") String username,
                       @Value("${app.admin.password}") String password) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        String usuarioAdmin = username.trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByUsername(usuarioAdmin)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsername(usuarioAdmin);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRol(Rol.ADMIN);
        admin.setNombre("Administrador");
        admin.setCreadoEn(LocalDateTime.now(clock));
        usuarioRepository.save(admin);

        log.info("Cuenta admin '{}' creada.", usuarioAdmin);
        if (CLAVE_DE_EJEMPLO.equals(password)) {
            log.warn("El admin usa la clave de ejemplo. Cámbiala con APP_ADMIN_PASSWORD antes de publicar el backend.");
        }
    }
}
