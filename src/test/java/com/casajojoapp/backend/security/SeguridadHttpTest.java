package com.casajojoapp.backend.security;

import com.casajojoapp.backend.repository.ItemMenuRepository;
import com.casajojoapp.backend.repository.MenuDiarioRepository;
import com.casajojoapp.backend.repository.PedidoRepository;
import com.casajojoapp.backend.repository.UsuarioRepository;
import com.casajojoapp.backend.service.MenuService;
import com.casajojoapp.backend.support.TestSupportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el backend de verdad (puerto aleatorio) y comprueba, con peticiones HTTP reales,
 * la tabla de accesos y el checklist de la guia de login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSupportConfig.class)
class SeguridadHttpTest {

    private static final String CSV_MENU = """
            ID,Categoria,Nombre,Descripcion,PrecioExtra,Disponible,Stock
            1,PROTEINA,Pollo al Jugo,Pechuga jugosa,3500,TRUE,50
            2,BEBIDA_POSTRE,Coca-Cola Zero 350ml,Bebida,1500,TRUE,50
            """;

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern CODIGO = Pattern.compile("\"codigoSeguimiento\"\\s*:\\s*\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int puerto;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Autowired
    private MenuService menuService;

    @Autowired
    private ItemMenuRepository itemMenuRepository;

    @Autowired
    private MenuDiarioRepository menuDiarioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestSupportConfig.StubMenuCsvSource source;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private Long itemId;
    private Long idUsuario;
    private String tokenAdmin;
    private String tokenUsuario;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        itemMenuRepository.deleteAll();
        menuDiarioRepository.deleteAll();

        source.setContenido(CSV_MENU);
        menuService.sincronizarConGoogleSheets();
        itemId = menuService.obtenerMenuVigente().items().get(0).id();

        Respuesta login = enviar("POST", "/api/v1/auth/login",
                "{\"username\":\"" + adminUsername + "\",\"password\":\"" + adminPassword + "\"}", null);
        assertThat(login.status()).isEqualTo(200);
        tokenAdmin = extraer(TOKEN, login.cuerpo());

        Respuesta registro = enviar("POST", "/api/v1/auth/registro",
                "{\"username\":\"segtest-ana\",\"password\":\"secreto123\",\"nombre\":\"Ana\"}", null);
        assertThat(registro.status()).isEqualTo(201);
        tokenUsuario = extraer(TOKEN, registro.cuerpo());
        idUsuario = Long.parseLong(extraer(ID, registro.cuerpo()));
    }

    @AfterEach
    void limpiar() {
        pedidoRepository.deleteAll();
        usuarioRepository.findAll().stream()
                .filter(u -> u.getUsername().startsWith("segtest-"))
                .forEach(usuarioRepository::delete);
    }

    @Test
    void sinTokenElPanelYElPerfilDan401() throws Exception {
        assertThat(enviar("GET", "/api/v1/pedidos", null, null).status()).isEqualTo(401);
        assertThat(enviar("GET", "/api/v1/pedidos/cola", null, null).status()).isEqualTo(401);
        assertThat(enviar("GET", "/api/v1/pedidos/resumen-cocina", null, null).status()).isEqualTo(401);
        assertThat(enviar("GET", "/api/v1/pedidos/1", null, null).status()).isEqualTo(401);
        assertThat(enviar("PATCH", "/api/v1/pedidos/1/estado", "{\"estado\":\"EN_PREPARACION\"}", null).status()).isEqualTo(401);
        assertThat(enviar("PATCH", "/api/v1/menu/estado", "{\"estado\":\"CERRADO\"}", null).status()).isEqualTo(401);
        assertThat(enviar("GET", "/api/v1/auth/me", null, null).status()).isEqualTo(401);
    }

    @Test
    void unUsuarioNormalRecibe403EnElPanelPeroPuedeVerSuPerfil() throws Exception {
        assertThat(enviar("GET", "/api/v1/pedidos", null, tokenUsuario).status()).isEqualTo(403);
        assertThat(enviar("GET", "/api/v1/pedidos/cola", null, tokenUsuario).status()).isEqualTo(403);
        assertThat(enviar("PATCH", "/api/v1/pedidos/1/estado", "{\"estado\":\"EN_PREPARACION\"}", tokenUsuario).status()).isEqualTo(403);
        assertThat(enviar("PATCH", "/api/v1/menu/estado", "{\"estado\":\"CERRADO\"}", tokenUsuario).status()).isEqualTo(403);

        Respuesta me = enviar("GET", "/api/v1/auth/me", null, tokenUsuario);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.cuerpo()).contains("\"username\":\"segtest-ana\"").doesNotContain("password");
    }

    @Test
    void elAdminAccedeAlPanelDeCocina() throws Exception {
        assertThat(enviar("GET", "/api/v1/pedidos", null, tokenAdmin).status()).isEqualTo(200);
        assertThat(enviar("GET", "/api/v1/pedidos/cola", null, tokenAdmin).status()).isEqualTo(200);
        assertThat(enviar("GET", "/api/v1/pedidos/resumen-cocina", null, tokenAdmin).status()).isEqualTo(200);
    }

    @Test
    void unTokenInvalidoCuentaComoSinSesion() throws Exception {
        assertThat(enviar("GET", "/api/v1/pedidos", null, "abc.def.ghi").status()).isEqualTo(401);

        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido(""), "abc.def.ghi");
        assertThat(pedido.status()).isEqualTo(201);
        assertThat(pedido.cuerpo()).contains("\"usuarioId\":null");
    }

    @Test
    void elMenuEsPublico() throws Exception {
        Respuesta menu = enviar("GET", "/api/v1/menu", null, null);

        assertThat(menu.status()).isEqualTo(200);
        assertThat(menu.cuerpo()).contains("Pollo al Jugo");
    }

    @Test
    void unInvitadoPuedePedirYElPedidoQuedaSinUsuario() throws Exception {
        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido(""), null);

        assertThat(pedido.status()).isEqualTo(201);
        assertThat(pedido.cuerpo()).contains("\"usuarioId\":null").contains("\"estado\":\"RECIBIDO\"");
    }

    @Test
    void conSesionElPedidoUsaElIdDelTokenYNoElDelCuerpo() throws Exception {
        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido("\"usuarioId\":999999,"), tokenUsuario);

        assertThat(pedido.status()).isEqualTo(201);
        assertThat(pedido.cuerpo()).contains("\"usuarioId\":" + idUsuario + ",");
        assertThat(pedido.cuerpo()).doesNotContain("999999");
    }

    @Test
    void elSeguimientoPorCodigoNoNecesitaSesion() throws Exception {
        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido(""), null);
        String codigo = extraer(CODIGO, pedido.cuerpo());

        Respuesta seguimiento = enviar("GET", "/api/v1/pedidos/seguimiento/" + codigo, null, null);

        assertThat(seguimiento.status()).isEqualTo(200);
        assertThat(seguimiento.cuerpo()).contains(codigo);
    }

    @Test
    void soloElAdminCambiaElEstadoDeUnPedido() throws Exception {
        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido(""), null);
        String ruta = "/api/v1/pedidos/" + extraer(ID, pedido.cuerpo()) + "/estado";
        String cuerpo = "{\"estado\":\"EN_PREPARACION\"}";

        assertThat(enviar("PATCH", ruta, cuerpo, null).status()).isEqualTo(401);
        assertThat(enviar("PATCH", ruta, cuerpo, tokenUsuario).status()).isEqualTo(403);

        Respuesta admin = enviar("PATCH", ruta, cuerpo, tokenAdmin);
        assertThat(admin.status()).isEqualTo(200);
        assertThat(admin.cuerpo()).contains("\"estado\":\"EN_PREPARACION\"");
    }

    @Test
    void soloElAdminAbreYCierraElMenu() throws Exception {
        Respuesta cierre = enviar("PATCH", "/api/v1/menu/estado", "{\"estado\":\"CERRADO\"}", tokenAdmin);
        assertThat(cierre.status()).isEqualTo(200);
        assertThat(cierre.cuerpo()).contains("\"estado\":\"CERRADO\"");

        Respuesta pedido = enviar("POST", "/api/v1/pedidos", cuerpoPedido(""), null);
        assertThat(pedido.status()).isEqualTo(422);

        assertThat(enviar("PATCH", "/api/v1/menu/estado", "{\"estado\":\"ABIERTO\"}", tokenAdmin).status()).isEqualTo(200);
    }

    @Test
    void elRegistroYElLoginRespondenSegunLaGuia() throws Exception {
        String repetido = "{\"username\":\"segtest-ana\",\"password\":\"secreto123\"}";
        assertThat(enviar("POST", "/api/v1/auth/registro", repetido, null).status()).isEqualTo(409);

        Respuesta invalido = enviar("POST", "/api/v1/auth/registro", "{\"username\":\"ab\",\"password\":\"123\"}", null);
        assertThat(invalido.status()).isEqualTo(400);

        Respuesta malo = enviar("POST", "/api/v1/auth/login", "{\"username\":\"segtest-ana\",\"password\":\"incorrecta\"}", null);
        assertThat(malo.status()).isEqualTo(401);
        assertThat(malo.cuerpo()).contains("Usuario o contraseña incorrectos.");

        Respuesta bueno = enviar("POST", "/api/v1/auth/login", repetido, null);
        assertThat(bueno.status()).isEqualTo(200);
        assertThat(bueno.cuerpo()).contains("\"rol\":\"USUARIO\"").doesNotContain("password");
    }

    private String cuerpoPedido(String extra) {
        return "{" + extra + "\"nombreCliente\":\"Invitado Test\",\"horaRetiroSolicitada\":\"13:30\","
                + "\"items\":[{\"itemMenuId\":" + itemId + ",\"cantidad\":1}]}";
    }

    private Respuesta enviar(String metodo, String ruta, String cuerpo, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));

        HttpResponse<String> respuesta = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Respuesta(respuesta.statusCode(), respuesta.body());
    }

    private String extraer(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        assertThat(m.find()).as("no se encontró %s en: %s", patron.pattern(), texto).isTrue();
        return m.group(1);
    }

    private record Respuesta(int status, String cuerpo) {
    }
}
