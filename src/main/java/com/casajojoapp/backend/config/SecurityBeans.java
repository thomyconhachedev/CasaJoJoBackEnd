package com.casajojoapp.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeans {

    /** BCrypt genera un salt distinto en cada hash: no hace falta una columna de salt. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * La autenticacion se hace con JWT (JwtAuthFilter) y con el login propio de UsuarioService.
     * Este bean evita que Spring Boot cree un usuario de ejemplo con una clave aleatoria en el log.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("La autenticación se hace con JWT.");
        };
    }
}
