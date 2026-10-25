package com.casajojoapp.backend.security;

import com.casajojoapp.backend.entity.Usuario;
import com.casajojoapp.backend.enums.Rol;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRETO = "clave-solo-para-pruebas-de-casajojo-0123456789-abcdef";

    private final JwtService jwtService = new JwtService(SECRETO, Duration.ofDays(7));

    @Test
    void generaUnTokenQueSePuedeLeer() {
        String token = jwtService.generarToken(usuario("ana", Rol.USUARIO));

        assertThat(jwtService.extraerUsername(token)).isEqualTo("ana");
    }

    @Test
    void guardaElRolComoClaim() {
        assertThat(jwtService.extraerRol(jwtService.generarToken(usuario("ana", Rol.USUARIO)))).isEqualTo("USUARIO");
        assertThat(jwtService.extraerRol(jwtService.generarToken(usuario("admin", Rol.ADMIN)))).isEqualTo("ADMIN");
    }

    @Test
    void rechazaUnTokenVencido() {
        JwtService yaVencido = new JwtService(SECRETO, Duration.ofSeconds(-60));
        String token = yaVencido.generarToken(usuario("ana", Rol.USUARIO));

        assertThrows(ExpiredJwtException.class, () -> jwtService.extraerUsername(token));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService ajeno = new JwtService("otra-clave-distinta-para-pruebas-0123456789-abcdefgh", Duration.ofDays(7));
        String token = ajeno.generarToken(usuario("ana", Rol.ADMIN));

        assertThrows(JwtException.class, () -> jwtService.extraerUsername(token));
    }

    @Test
    void rechazaUnTokenAlterado() {
        String token = jwtService.generarToken(usuario("ana", Rol.USUARIO));
        int posicion = token.indexOf('.') + 5;
        char original = token.charAt(posicion);
        String alterado = token.substring(0, posicion) + (original == 'A' ? 'B' : 'A') + token.substring(posicion + 1);

        assertThrows(JwtException.class, () -> jwtService.extraerUsername(alterado));
    }

    @Test
    void rechazaTextoQueNoEsUnToken() {
        assertThrows(JwtException.class, () -> jwtService.extraerUsername("esto-no-es-un-token"));
    }

    @Test
    void noAceptaUnaClaveDebil() {
        assertThrows(JwtException.class, () -> new JwtService("corta", Duration.ofDays(1)));
    }

    private Usuario usuario(String username, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRol(rol);
        return usuario;
    }
}
