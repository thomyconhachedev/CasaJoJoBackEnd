package com.casajojoapp.backend.security;

import com.casajojoapp.backend.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Genera y valida los tokens. Claims: sub = username, rol = USUARIO | ADMIN.
 * Usa la hora real del sistema (no el reloj del negocio) porque el vencimiento es un asunto de seguridad.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final Duration duracion;

    @Autowired
    public JwtService(@Value("${app.jwt.secret}") String secreto,
                      @Value("${app.jwt.expiracion-dias:7}") long expiracionDias) {
        this(secreto, Duration.ofDays(expiracionDias));
    }

    public JwtService(String secreto, Duration duracion) {
        // Falla al arrancar si la clave tiene menos de 32 bytes: mejor eso que firmar con una clave debil.
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = duracion;
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    /** Lanza JwtException si el token esta vencido, alterado o mal formado. */
    public String extraerUsername(String token) {
        return claims(token).getSubject();
    }

    public String extraerRol(String token) {
        return claims(token).get("rol", String.class);
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
    }
}
