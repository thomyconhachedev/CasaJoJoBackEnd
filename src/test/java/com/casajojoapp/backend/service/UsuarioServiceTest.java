package com.casajojoapp.backend.service;

import com.casajojoapp.backend.dto.request.LoginRequest;
import com.casajojoapp.backend.dto.request.RegistroRequest;
import com.casajojoapp.backend.dto.response.AuthResponse;
import com.casajojoapp.backend.entity.Usuario;
import com.casajojoapp.backend.exception.BusinessRuleException;
import com.casajojoapp.backend.repository.UsuarioRepository;
import com.casajojoapp.backend.support.TestSupportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestSupportConfig.class)
class UsuarioServiceTest {

    private static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos.";

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @AfterEach
    void limpiar() {
        usuarioRepository.findAll().stream()
                .filter(u -> u.getUsername().startsWith("svc-"))
                .forEach(usuarioRepository::delete);
    }

    @Test
    void registraUnClienteConRolUsuarioYClaveCifrada() {
        AuthResponse r = usuarioService.registrarCliente(
                new RegistroRequest("svc-Ana", "secreto123", " Ana Pérez ", "+56912345678"));

        assertThat(r.token()).isNotBlank();
        assertThat(r.id()).isNotNull();
        assertThat(r.username()).isEqualTo("svc-ana");
        assertThat(r.rol()).isEqualTo("USUARIO");
        assertThat(r.nombre()).isEqualTo("Ana Pérez");
        assertThat(r.telefono()).isEqualTo("+56912345678");

        Usuario guardado = usuarioRepository.findByUsername("svc-ana").orElseThrow();
        assertThat(guardado.getPassword()).isNotEqualTo("secreto123").startsWith("$2");
        assertThat(passwordEncoder.matches("secreto123", guardado.getPassword())).isTrue();
    }

    @Test
    void rechazaUnUsernameRepetidoSinDistinguirMayusculas() {
        usuarioService.registrarCliente(new RegistroRequest("svc-luis", "secreto123", null, null));

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarCliente(new RegistroRequest("SVC-Luis", "otraclave1", null, null)));

        assertThat(e.getStatus()).isEqualTo(409);
    }

    @Test
    void rechazaUnaClaveCortaOMuyLarga() {
        BusinessRuleException corta = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarCliente(new RegistroRequest("svc-corta", "123", null, null)));
        BusinessRuleException larga = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarCliente(new RegistroRequest("svc-larga", "x".repeat(80), null, null)));

        assertThat(corta.getStatus()).isEqualTo(400);
        assertThat(larga.getStatus()).isEqualTo(400);
        assertThat(usuarioRepository.existsByUsername("svc-corta")).isFalse();
    }

    @Test
    void iniciaSesionConLaClaveCorrecta() {
        usuarioService.registrarCliente(new RegistroRequest("svc-marta", "secreto123", "Marta", null));

        AuthResponse r = usuarioService.login(new LoginRequest("SVC-Marta", "secreto123"));

        assertThat(r.token()).isNotBlank();
        assertThat(r.username()).isEqualTo("svc-marta");
        assertThat(r.rol()).isEqualTo("USUARIO");
    }

    @Test
    void unLoginIncorrectoUsaSiempreElMismoMensaje() {
        usuarioService.registrarCliente(new RegistroRequest("svc-pedro", "secreto123", null, null));

        BusinessRuleException claveMala = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login(new LoginRequest("svc-pedro", "incorrecta")));
        BusinessRuleException noExiste = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login(new LoginRequest("svc-fantasma", "secreto123")));

        assertThat(claveMala.getStatus()).isEqualTo(401);
        assertThat(noExiste.getStatus()).isEqualTo(401);
        assertThat(claveMala.getMessage()).isEqualTo(MENSAJE_GENERICO);
        assertThat(noExiste.getMessage()).isEqualTo(MENSAJE_GENERICO);
    }

    @Test
    void elAdminSembradoPuedeIniciarSesion() {
        AuthResponse r = usuarioService.login(new LoginRequest(adminUsername, adminPassword));

        assertThat(r.rol()).isEqualTo("ADMIN");
        assertThat(r.token()).isNotBlank();
    }

    @Test
    void elPerfilDevuelveLosDatosYElMismoToken() {
        AuthResponse registro = usuarioService.registrarCliente(
                new RegistroRequest("svc-eva", "secreto123", "Eva", "+56911112222"));

        AuthResponse perfil = usuarioService.obtenerPerfil(registro.id(), registro.token());

        assertThat(perfil.username()).isEqualTo("svc-eva");
        assertThat(perfil.nombre()).isEqualTo("Eva");
        assertThat(perfil.token()).isEqualTo(registro.token());
    }

    @Test
    void laRespuestaNuncaIncluyeLaClave() {
        assertThat(Arrays.stream(AuthResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("password", "clave", "hash");
    }
}
