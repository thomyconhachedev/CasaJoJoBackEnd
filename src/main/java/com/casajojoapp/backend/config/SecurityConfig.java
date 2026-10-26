package com.casajojoapp.backend.config;

import com.casajojoapp.backend.repository.UsuarioRepository;
import com.casajojoapp.backend.security.JwtAuthFilter;
import com.casajojoapp.backend.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Quien puede llamar a que:
 *  - Publico: menu, sincronizar, registro, login, crear pedido (invitado o con sesion) y seguimiento por codigo.
 *  - Autenticado: /auth/me.
 *  - Solo ADMIN: panel de cocina (listar, cola, resumen, consultar por id, cambiar estado) y abrir/cerrar el menu.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UsuarioRepository usuarioRepository) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, usuarioRepository);

        http
                .csrf(csrf -> csrf.disable()) // API sin estado y sin cookies: no aplica CSRF
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/menu/sincronizar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/seguimiento/*").permitAll()
                        // El usuario normal consulta SUS pedidos con su token
                        // (la cola completa de aca arriba es solo de admin).
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/mios").authenticated()
                        .requestMatchers("/api/v1/menu/**", "/api/v1/pedidos/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                escribirError(response, 401, "Debes iniciar sesión para usar este recurso."))
                        .accessDeniedHandler((request, response, ex) ->
                                escribirError(response, 403, "No tienes permisos para realizar esta acción.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Mismo formato de error que el resto de la API: {"error": "..."}. */
    private static void escribirError(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
