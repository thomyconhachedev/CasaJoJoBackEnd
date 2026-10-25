package com.casajojoapp.backend.security;

import com.casajojoapp.backend.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Si llega "Authorization: Bearer <token>" valido, carga el Usuario y lo deja en el SecurityContext.
 * Un token invalido o vencido se trata como "sin sesion": las rutas protegidas responden 401 y las
 * publicas (por ejemplo un pedido de invitado) siguen funcionando.
 *
 * No es un bean a proposito: asi Spring Boot no lo registra tambien como filtro global del servlet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");

        if (cabecera != null && cabecera.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cabecera.substring(7).trim();
            try {
                String username = jwtService.extraerUsername(token);
                usuarioRepository.findByUsername(username).ifPresent(usuario -> {
                    // El rol se lee de la base y no del token: si un admin pierde el rol, deja de serlo al instante.
                    List<SimpleGrantedAuthority> autoridades =
                            List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(usuario, null, autoridades));
                });
            } catch (JwtException | IllegalArgumentException e) {
                // token invalido o vencido: se ignora
            }
        }

        filterChain.doFilter(request, response);
    }
}
