# Plan de Implementación — Brechas entre CasaJoJoBackEnd y el *Manual Monolito Spring Boot CasaJoJoApp*

> Documento de referencia técnica. Contrasta el estado real del repositorio contra los requisitos
> del manual `Manual Monolito Spring Boot CasaJoJoApp.pdf` (5 páginas) y justifica **qué falta**,
> **por qué debe implementarse** y **qué mejoras adicionales son posibles**.

| | |
|---|---|
| **Proyecto** | CasaJoJoBackEnd (`com.casajojoapp.backend`) |
| **Documento de referencia** | `Manual Monolito Spring Boot CasaJoJoApp.pdf` |
| **Fecha de la revisión** | 2026-10-21 |
| **Estado verificado** | Spring Boot 4.1.1 · Java 21 · `mvn test` → **37/37 pruebas en verde** |
| **Alcance de este documento** | Solo análisis y planificación. **No se modificó código.** |

---

## 1. Propósito

El manual define la arquitectura objetivo del backend de CasaJoJoApp: un **monolito modular** con
cuatro módulos de servicio (Usuario, Menú/Inventario, Pedidos/Cocina, Modificaciones), separación
estricta Controller → Service → Repository → MySQL, y un contrato DTO cerrado.

Este documento responde tres preguntas:

1. **¿Qué exige el manual y qué no está implementado?**
2. **¿Por qué vale la pena implementarlo?** (impacto, riesgo y costo de no hacerlo)
3. **¿Qué mejoras se pueden añadir más allá del manual?**

---

## 2. Método de verificación

La comparación no se hizo por lectura del PDF solamente. Cada punto se contrastó contra el código:

- Inventario completo de `src/main/java` (40 archivos) y `src/test/java` (6 archivos).
- Lectura íntegra de las 4 entidades, 3 repositorios, 2 servicios + implementaciones, 2 controllers,
  8 DTOs, 3 enums, 4 clases de excepción, el parser, la fuente CSV y el `ClockConfig`.
- Revisión de `pom.xml`, `application.properties` (producción y test), `Dockerfile`, `compose.yaml`,
  `.env` / `.env.example`, `.gitignore` y `.dockerignore`.
- Ejecución real de `.\mvnw.cmd -B test` → **37 pruebas, 0 fallos, BUILD SUCCESS**.
- Búsqueda explícita de `SecurityConfig`, `OpenApiConfig`, `CorsConfig`, `Usuario`, `LoginRequest`,
  `RegistroRequest`, `SolicitarModificacion`, `ModificacionPedido`, `RolUsuario`, `TipoIdentificacion`,
  `CategoriaItem`, `springdoc`, `security` y `BCrypt` en todo el repositorio → **2 coincidencias,
  solo en `README.md:56` y `PedidoServiceImpl:113`** (ambas referencias textuales, no implementaciones).

---

## 3. Resumen ejecutivo

### 3.1 Cobertura global

| Métrica | Valor |
|---|---|
| Clases nombradas en el §3 del manual | 33 |
| Clases presentes | **20 (61%)** |
| Clases ausentes | **13 (39%)** |
| Módulos de servicio completos | **2 de 4** (Menú, Pedidos) |
| Módulos de servicio ausentes | **2 de 4** (Usuario, Modificación de pedidos) |
| Secciones del manual con brecha | §3, §5-Módulo 1, §6-Módulo 4, §8 (parcial) |
| Secciones del manual cumplidas | §1, §2, §4, §5-Módulo 2, §6-Módulo 3, §7 |

### 3.2 Semáforo por sección del manual

| § | Requisito | Estado |
|---|---|---|
| 1 | Monolito modular, un JAR, paquetes por dominio | 🟢 Cumple |
| 2 | Transacciones ACID con `@Transactional` | 🟢 Cumple |
| 3 | Estructura de paquetes | 🟡 Parcial (20/33) |
| 4 | Flujo Controller → Service → Repository → MySQL | 🟢 Cumple |
| 4 | Regla de Oro: Entity nunca se devuelve al Controller | 🟢 Cumple |
| 5 | **Módulo 1 — UsuarioService** | 🔴 **Ausente** |
| 5 | Módulo 2 — MenuService (Sheets + caché local) | 🟢 Cumple |
| 6 | Módulo 3 — PedidoService (transaccional, FIFO, estados) | 🟢 Cumple |
| 6 | **Módulo 4 — ModificacionPedidoService** | 🔴 **Ausente** |
| 6 | Inmutabilidad financiera (`precio_historico`) | 🟢 Cumple |
| 7 | Contrato DTO entrada/salida | 🟢 Cumple (superset compatible) |
| 8 | `GlobalExceptionHandler` con códigos semánticos | 🟡 Parcial (falta catch-all) |

### 3.3 Lo que ya está resuelto y no debe tocarse

Estos puntos cumplen el manual y tienen pruebas que los respaldan:

- **Copia local en MySQL.** `MenuServiceImpl.sincronizarConGoogleSheets()` (`:73`) lee el Sheet y
  escribe en `item_menu`; `obtenerMenuVigente()` (`:90`) y `crearPedido()` nunca consultan Sheets.
  Es exactamente el beneficio de caché que pide el manual.
- **Bloqueo pesimista del stock.** `ItemMenuRepository:15` — `@Lock(PESSIMISTIC_WRITE)` sobre
  `findWithLockById`. Cumple "soporte concurrente".
- **Anti-deadlock.** `PedidoServiceImpl:78` agrupa y ordena los ítems en un `TreeMap`, de modo que
  dos pedidos concurrentes toman los bloqueos siempre en el mismo orden. Decisión de diseño
  correcta y superior a lo que el manual exige.
- **Atomicidad del pedido.** `@Transactional` en `crearPedido` (`:68`): si un ítem falla, se revierte
  el descuento de los demás. Verificado por `sinStockSuficienteNoSeGuardaNadaYSeRevierteElDescuento`.
- **Precio congelado.** `DetallePedido.precioHistorico` (`:39`) y `montoTotal` recalculado por el
  servidor (`:135`). Cubierto por `elPrecioQuedaCongeladoAunqueElMenuCambie`.
- **Máquina de estados estricta.** `EstadoPedido.siguiente()` obliga a avanzar de uno en uno y
  rechaza retrocesos. Cubierto por `laMaquinaDeEstadosSoloAvanzaDeAUno`.
- **Reloj de negocio con zona horaria.** `config/ClockConfig.java` + `app.zona-horaria=America/Santiago`
  (`application.properties:14`). El "día del menú" no depende de la zona del contenedor. El manual
  no lo pedía; es una mejora real del proyecto.
- **Regla de Oro.** Todos los returns de los controllers son `record` DTO. Ninguna Entity escapa a la API.
- **Códigos HTTP semánticos** coherentes con `API.md`: 400 / 404 / 409 / 422 / 502.

---

## 4. Brechas funcionales — requisitos del manual no implementados

### B-01 — Módulo 1: `UsuarioService` (autenticación e identidad mixta)

**Qué exige el manual** (§5, pág. 3): "Administra el registro, autenticación y perfiles de acceso.
Soporta tres modalidades de cliente: **ANONIMA** (pedidos rápidos ingresando solo nombre y teléfono al
confirmar), **TEMPORAL** (perfil con identificador UUID generado desde la App Android para guardar
historial local) y **FORMAL** (registro con email y contraseña cifrada mediante `BCryptPasswordEncoder`)".
Métodos clave: `login(LoginRequest)`, `registrarCliente(RegistroRequest)`, `obtenerPerfil(Long id)`.

**Estado actual.** No existe ninguna pieza de este módulo:

| Clase exigida | Estado |
|---|---|
| `entity/Usuario` | ❌ ausente |
| `repository/UsuarioRepository` | ❌ ausente |
| `service/UsuarioService` + `service/impl/UsuarioServiceImpl` | ❌ ausente |
| `controller/AuthController` | ❌ ausente |
| `dto/request/LoginRequest`, `dto/request/RegistroRequest` | ❌ ausentes |
| `enums/RolUsuario`, `enums/TipoIdentificacion` | ❌ ausentes |
| `config/SecurityConfig` (BCrypt) | ❌ ausente |
| Dependencia `spring-boot-starter-security` | ❌ ausente en `pom.xml` |

Lo único que hay es `Pedido.usuarioId` (`@Column(name = "usuario_id")`), un `Long` escalar sin
integridad referencial, que el propio `README.md:56` documenta como "reservado para una futura
integración con usuarios registrados".

**Por qué implementarlo.**

1. **Es el bloqueante de producción.** Sin autenticación, `GET /api/v1/pedidos` devuelve **todos** los
   pedidos con nombre y teléfono del cliente, y `PATCH /api/v1/pedidos/{id}/estado` deja que
   cualquiera altere el ciclo de vida de una comanda (incluido marcarla `ENTREGADO`).
   El `README.md:126` ya lo admite: *"Antes de exponer la API a Internet, implementa y verifica
   autenticación y autorización"*. Hoy la API está pensada para estar expuesta en una EC2
   (`API.md:3` documenta `http://<IP-DE-LA-EC2>:8080`).
2. **Datos personales sin base legal ni trazabilidad.** Nombre, teléfono y observaciones —que el
   propio `API.md:67` describe como lugar donde el cliente declara "restricciones alimentarias"—
   se storing en texto plano y se exponen sin control de acceso.
3. **El manual lo exige de forma explícita** y es la base de las 3 modalidades de cliente; sin él, el
   `usuarioId` que el frontend ya envía (`API.md:54`) es un campo muerto.
4. **Habilita las 3 modalidades**: la TEMPORAL (UUID desde la app) es lo que realmente necesita el
   flujo actual —pedidos con historial local sin exigir registro— y hoy no existe ninguna forma de
   asociar un cliente recurrente a sus comandas.

**Impacto de no hacerlo.** Exposición de PII y manipulación no autorizada de comandas en un servicio
que, por diseño, se despliega en una IP pública. Un incidente de lectura de la lista de pedidos
(expuestos en la misma pantalla de cocina) es trivial de ejecutar con un `curl`.

**Qué implica.**

- `spring-boot-starter-security` + `SecurityConfig` con `BCryptPasswordEncoder`.
- `Usuario` con: id, `tipoIdentificacion` (UUID / email), identificador, `rol`, `passwordHash`
  (nullable para anónimo/temporal), nombre, teléfono, `createdAt`.
- `UsuarioServiceImpl.login()` que devuelve JWT (o token de sesión) con el rol y el id.
- `AuthController` con `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/perfil`.
- Reglas de autorización por rol: cliente vs. administrador (cocina/operación).
- En `Pedido`, evaluar si `usuarioId` pasa a ser `@ManyToOne` real o permanece escalar.

**Criterio de aceptación.** Las tres modalidades pueden crear pedidos; los endpoints de cocina y de
administración exigen credencial válida; un cliente solo ve sus propios pedidos; un token expirado
recibe 401 y uno sin rol recibe 403; ninguna ruta de escritura de pedidos es accesible sin rol.

---

### B-02 — Módulo 4: `ModificacionPedidoService` (ajustes post-envío)

**Qué exige el manual** (§6, pág. 4): "Permite gestionar requerimientos del cliente para modificar
pedidos antes de su preparación. **Solicitud:** el cliente registra la petición siempre que el pedido
esté en estado `RECIBIDO`. **Resolución Administrativa:** el personal aprueba o rechaza la solicitud.
**Si se aprueba, se reajusta automáticamente el stock y se recalcula el total.**"
Métodos clave: `solicitarModificacion(request)`, `aprobarORechazarModificacion(id, aprobada)`.

**Estado actual.** No existe el servicio, la interfaz, la implementación, el DTO
`SolicitarModificacionRequest`, la entidad de modificación ni los endpoints correspondientes.
No hay ninguna ruta en `PedidoController` que permita alterar un pedido ya enviado.

**Por qué implementarlo.**

1. **Cierra el ciclo operativo real.** Hoy un cliente que pidió por error 3 portions y tiene que
   cumplir la hora de retiro solo tiene dos opciones: cancelar (no existe) o esperar. Es la
   funcionalidad que evita el desperdicio de comida preparada y el reclamo en el mostrador.
2. **El manual la define con una consecuencia técnica concreta** —"reajusta automáticamente el stock
   y recalcula el total"— y eso no es trivial: implica una transacción que devuelve stock a los
   ítems que salen y descuenta a los que entran, más el recálculo de `monto_total` con el mismo
   criterio de `precio_historico` congelado (`PedidoServiceImpl:135`).
3. **Sin esta pieza, la lógica de consistencia de stock que el proyecto ya implementó**
   (bloqueo pesimista, orden de bloqueo estable, transaccionalidad) no tiene su segundo caso de uso
   — hoy solo se exercise en la creación.
4. **Es un módulo de negocio, no de infraestructura**: define el diferencial operativo del sistema.

**Impacto de no hacerlo.** Reclamos y merma; y una divergencia entre lo que el manual garantiza
("reajusta stock y recalcula total") y lo que el código hace.

**Qué implica.**

- Entidad `ModificacionPedido` (pedido, tipo de cambio, items solicitados, estado
  `PENDIENTE/APROBADA/RECHAZADA`, motivo, timestamps, quién resolvió).
- `ModificacionPedidoServiceImpl.solicitarModificacion()` que valida `estado == RECIBIDO`.
- `aprobarORechazarModificacion(id, aprobada)` en transacción única: reponer stock de ítems
  eliminados, descontar stock de los agregados (con `findWithLockById`, **en orden ascendente de id**
  para no volver a introducir deadlocks), recalcular `monto_total` y actualizar las líneas de
  `pedido_detalle` con su `precio_historico`.
- Endpoints cliente (`POST /api/v1/pedidos/{id}/modificaciones`) y admin
  (`GET /api/v1/modificaciones`, `PATCH /api/v1/modificaciones/{id}`).
- Reutilizar `MenuService.descontarStock()` y crear el método simétrico de reposición.

**Criterio de aceptación.** Aprobar una modificación que agrega un ítem descuenta stock y sube el
total; que quita un ítem repone stock y baja el total; que cambia cantidades ajusta ambos; una
solicitud sobre un pedido que ya no está en `RECIBIDO` se rechaza; la operación es atómica (si un
ítem no tiene stock, no se aplica ningún ajuste parcial).

---

### B-03 — `config/OpenApiConfig` (documentación de la API)

**Qué exige el manual** (§3, pág. 2): el paquete `config/` debe contener `SecurityConfig`,
`OpenApiConfig` y `CorsConfig`.

**Estado actual.** No existe la clase ni la dependencia `springdoc-openapi`. No hay
`/v3/api-docs` ni interfaz Swagger.

**Por qué implementarlo.** La App Android es el cliente de esta API y `API.md` está escrito a mano:
cada endpoint nuevo obliga a actualizar dos documentos que pueden divergir. Una especificación
OpenAPI generada desde el código elimina esa clase de error por construcción y sirve para generar
el cliente o hacer pruebas de contrato.

**Criterio de aceptación.** `GET /v3/api-docs` devuelve la especificación completa de los endpoints
existentes y de los nuevos; el documento se regenera automáticamente al compilar.

---

### B-04 — `config/CorsConfig`

**Qué exige el manual** (§3, pág. 2).

**Estado actual.** No existe. No hay `WebMvcConfigurer` ni propiedad `spring.mvc.cors.*`.

**Por qué implementarlo.** Prioridad **baja** para el caso de uso actual: una app Android no
sufre CORS (no es un navegador). Sin embargo, será necesario en cuanto exista un panel web de
cocina o de administración, y el manual lo pide de forma explícita.

**Criterio de aceptación.** El origen del panel web queda habilitado de forma explícita y
restringida (no comodín), configurable por variable de entorno.

---

### B-05 — Enums de dominio faltantes: `CategoriaItem`

**Qué exige el manual** (§3, pág. 2): `enums/` → `EstadoPedido`, `CategoriaItem`, `RolUsuario`,
`TipoIdentificacion`. Los dos últimos dependen de B-01.

**Estado actual.** `RolUsuario` y `TipoIdentificacion` no existen (van con B-01).
`CategoriaItem` tampoco: `ItemMenu.categoria` es un `String` libre de 50 caracteres
(`ItemMenu.java:26`), y el parser la normaliza a mayúsculas con fallback `"GENERAL"`
(`MenuCsvParser`, columna `CATEGORIA`).

**Por qué implementarlo.** Un `String` libre en la base de datos es la causa clásica de datos
sucios: `"PROTEINA"`, `"proteina"`, `"Proteína"` y `"PROTEÍNA"` acaban siendo cuatro categorías
distintas en `item_menu` y en los reportes de cocina. Un enum con un valor por categoría oficial
convierte el error en un fallo de parseo localized y visible en la sincronización, en vez de en
filas silenciosamente distintas. (La normalización NFD ya existente en `MenuCsvParser.normalizar`
es una buena base y se puede reutilizar.)

**Criterio de aceptación.** `ItemMenu.categoria` referencia un enum; el parser mapea la columna del
Sheet al enum y registra las categorías desconocidas en el log en lugar de crearlas.

---

## 5. Brechas de robustez y de contrato

### B-06 — `GlobalExceptionHandler` sin catch-all genérico

**Qué exige el manual** (§8, pág. 5): "Captura **excepciones del sistema** (BusinessRuleException,
ResourceNotFoundException) y retorna respuestas JSON estandarizadas con códigos HTTP semánticos
(400, 404, 409, 422), **sin exponer trazas internas de la base de datos**".

**Estado actual.** `GlobalExceptionHandler` (`:21`) maneja `BusinessRuleException`,
`ResourceNotFoundException`, `SincronizacionException`, `MethodArgumentNotValidException`,
`HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` y `DataAccessException`.
**Falta un `@ExceptionHandler(Exception.class)`.**

**Por qué implementarlo.** El contrato de `API.md:3` es explícito: *"Todo es JSON. Los errores
responden `{"error": "mensaje"}`"*. Hoy, ante una excepción no prevista (un `NullPointerException`,
un `IllegalStateException` de la configuración, un error de serialización), Spring devuelve su
cuerpo por defecto:

```json
{"timestamp":"2026-10-21T13:16:05Z","status":500,"error":"Internal Server Error","path":"/api/v1/pedidos"}
```

El cliente Android parsea `error` y recibe un cuerpo con otra forma → error de deserialización en
lugar de un mensaje de negocio. Además, ese cuerpo **sí** incluye la ruta y el timestamp, rompiendo
la homogeneidad del contrato. La línea 62 ya demuestra la intención: `DataAccessException` se
traduce a un 500 genérico con mensaje amigable. Solo falta extenderlo al resto.

**Qué implica.** Un `@ExceptionHandler(Exception.class)` que registre el error completo en el log
(con stack trace, **solo en servidor**) y devuelva `500` con `{"error": "..."}` y sin detalle técnico.
Idealmente un identificador de correlación para poder cruzar el log del servidor con el error que
ve el cliente.

**Criterio de aceptación.** Ninguna respuesta de error del API tiene una forma distinta de
`{"error": "..."}`. Añadir el test correspondiente.

---

### B-07 — Contrato HTTP sin una sola prueba

**Qué exige el manual** (§4, pág. 2): el Controller "recibe el JSON, valida la estructura con
`@Valid` sobre DTOs y mapea códigos de respuesta HTTP".

**Estado actual.** Las 37 pruebas cubren el parser CSV y los dos servicios (integración con H2).
**No existe ni un test de controller**: no hay `@WebMvcTest`, ni `MockMvc`, ni `WebTestClient`.
Los códigos HTTP —201, 400, 404, 409, 422, 502— y la forma del cuerpo de error no están verificados
por ninguna prueba.

**Por qué implementarlo.** El contrato HTTP es la superficie pública del sistema y el punto de
acoplamiento con la App Android. Sin pruebas de ese nivel, cualquier refactor del service puede
cambiar un 409 por un 422 sin que nada lo detecte. Además, la cobertura de errores es donde más
bugs se escapan: los tests actuales invocan el service y verifican `e.getStatus()`, pero no
verifican que el handler traduzca ese status a la respuesta HTTP correcta.

**Qué implica.** Un `@WebMvcTest` por controller, con `@MockBean` del service, cubriendo al menos:
201 con cuerpo válido; 400 con `@Valid` y con JSON malformado; 404 con id inexistente; 409 y 422
mapeados desde el service; 502 en fallo de sincronización; y el cuerpo `{"error": "..."}` en todos
los casos.

**Criterio de aceptación.** Existe al menos un test por endpoint de la API, y el contrato de
`API.md` y el manual quedan codificados en pruebas ejecutables.

---

### B-08 — `GET /api/v1/pedidos` sin paginación

**Qué exige el manual.** No lo menciona (es un requisito operativo implícito).

**Estado actual.** `PedidoController.listar()` (`:33`) devuelve `List<PedidoResponse>` completo, sin
paginación ni límite, y `PedidoRepository.findAllByOrderByHoraRetiroAscIdAsc()` carga todas las
filas. Cada `PedidoResponse` carga además sus `detalles` (relación `@OneToMany` con
`cascade = ALL`).

**Por qué implementarlo.** La tabla `pedido` crece de forma monotona (nunca se purga). A los pocos
miles de pedidos, esta respuesta carga decenas de MB, demora varios segundos y —con
`open-in-view=false`, `application.properties:10`— provoca N+1 queries sobre `pedido_detalle`. Es el
endpoint que consume el polling de la app (ver `API.md:90-93`: la app consulta el estado del pedido
repetidamente), así que su costo se multiplica por cada cliente activo.

**Qué implica.** `Pageable` + `Page<PedidoResponse>` con tamaño por defecto (p. ej. 50) y soporte de
`page`/`size`/`sort`; índice sobre `(estado, hora_retiro)` para dar soporte a la consulta de la cola.

**Criterio de aceptación.** `GET /api/v1/pedidos` devuelve una página con metadatos de paginación y
el número de queries no crece con el tamaño de la tabla.

---

### B-09 — La cola de cocina y el resumen dependen del nombre, no del id

**Qué exige el manual** (§6, pág. 4): "Cola FIFO para Cocina"; el módulo 3 también expone un
"resumen de cocina" con la cantidad total a preparar de cada ítem.

**Estado actual.** `PedidoServiceImpl.obtenerResumenCocina()` (`:188`) agrupa con
`porItem.merge(detalle.getNombreItem(), detalle.getCantidad(), Integer::sum)`. La clave es el
**nombre** del producto, no el `itemMenuId`.

**Por qué implementarlo.** Si el catálogo tiene dos productos con el mismo nombre en categorías
distintas (por ejemplo "Arroz" en `ACOMPANAMIENTO` y "Arroz" en `ENSALADA`), sus cantidades se
suman en una sola fila y la cocina recibe un número de unidades que no corresponde con ningún
producto. Con `categoria` como `String` libre (B-05) el riesgo es real, no teórico. La congelación
del nombre en `pedido_detalle` (que es intencionada, para el histórico) choca con el uso del nombre
como clave de agrupación.

**Qué implica.** Agrupar por `itemMenuId` y llevar el nombre como dato de la respuesta; para las
líneas cuyo `itemMenuId` quedó huérfano (el ítem fue borrado del menú y la FK no existe por
diseño, ver `README.md:86`), agrupar por un surrogate estable (p. ej. `itemMenuId` + nombre) o por
una clave propia de la línea.

**Criterio de aceptación.** Dos ítems homónimos de categorías distintas aparecen como filas
separadas en el resumen.

---

## 6. Brechas de configuración, despliegue y documentación

### B-10 — `README.md` describe una configuración que ya no es la del código

**Estado actual.** `README.md:112` afirma: *"En el estado original del proyecto, usa MySQL en
`localhost:3306`, base `casajojo`, usuario `root` y contraseña vacía"*. Pero
`application.properties:3-5` exige variables de entorno **sin valor por defecto**:

```
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

**Por qué implementarlo.** Quien siga el README al pie de la letra.Configura un `root` sin clave en
un MySQL local, o bien se detiene en un error de arranque críptico por variable de entorno faltante
(`Could not resolve placeholder 'SPRING_DATASOURCE_URL'`). En un entorno compartido, además, la
documentación-normalizada de "root con contraseña vacía" es exactamente el tipo de instrucción que
no debe quedar escrita en un README de repositorio.

**Qué implica.** Actualizar la sección "Configuración de base de datos" del README para que describa
el mecanismo real (variables de entorno + `compose.yaml` + `.env.example`), sin exponer credenciales
de ejemplo que inducan a la práctica.

**Criterio de aceptación.** Quien clone el repositorio y siga el README logra levantar el stack con
`docker compose up` sin conocer variables que no estén documentadas.

---

### B-11 — URL real del Google Sheet hardcodeada en el repositorio

**Estado actual.** `application.properties:12` fija la URL del Sheet publicado en el código:

```
menu.sheet.csv-url=https://docs.google.com/spreadsheets/d/e/2PACX-1vT3wdIy7ap7gej8uVrufzVC_EmniLPJqrEv2zRYd9pS8e9E8m1C7vZrP367uXXCR4hOWo5EjcI9eyF3/pub?output=csv
```

**Por qué implementarlo.** Es un identificador (`/d/e/2PACX-...`) del documento real del negocio
publicado en el repositorio. Si el Sheet es público ("pub"), cualquiera con la URL puede leer el
menú; y si algún día deja de serlo o se rota, el cambio exige un commit y un redeploy en lugar de
una variable de entorno. La URL es también el punto de rotación natural cuando cambie el Sheet.

**Qué implica.** `menu.sheet.csv-url=${MENU_SHEET_CSV_URL:...}` con valor por defecto vacío o con la
URL actual, y documentarla en `.env.example`.

**Criterio de aceptación.** La URL del Sheet se puede cambiar por entorno sin recompilar; ninguna
credencial ni identificador sensible queda fijo en `application.properties`.

---

### B-12 — La columna `PrecioExtra` del Sheet se interpreta como precio final

**Estado actual.** `MenuCsvParser` mapea al campo `PRECIO` **toda columna cuyo título empiece por
"precio"** (`titulo.startsWith("precio")`), y el Sheet real se llama `PrecioExtra`. El valor se
parsea con `replaceAll("[^0-9]", "")`, es decir, se eliminan todos los caracteres no numéricos.

**Por qué implementarlo.** Dos riesgos concretos:

1. **La semántica de la columna.** Si `PrecioExtra` es un *recargo* (extra) y no el precio de venta
   del producto, entonces `item_menu.precio` queda con el valor equivocado para todos los ítems y
   todos los totales calculados por el servidor son incorrectos — el error se propaga a `monto_total`.
   El nombre de la columna en el Sheet es la señal, y hoy nadie lo verifica.
2. **El parseo numérico es frágil.** `"$1.500"` → `1500` ✅ (CLP sin decimales, correcto), pero
   `"3500.50"` → `350050` ❌ y `"1.500,50"` → `150050` ❌. Con precios enteros en CLP no hay problema;
   en cuanto el catálogo incorpore un valor con decimales, el error es silencioso.

**Qué implica.** Verificar contra el Sheet real qué significa `PrecioExtra` y qué formato de precio
tiene. Si es un recargo, separar el concepto (columna `precio` y columna `precio_extra` en
`ItemMenuImportado`, o directamente leer una columna `precio` si el Sheet la expone). Blindar el
parseo para rechazar —en vez de truncar silenciosamente— formatos con decimales.

**Criterio de aceptación.** El precio persistido coincide con el precio de venta real del producto
en el Sheet, verificado contra una muestra; un formato numérico inesperado produce un error de
sincronización visible, no un valor corrupto.

---

### B-13 — `ddl-auto=update` sin migraciones versionadas

**Estado actual.** `application.properties:8` usa
`spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}` y `compose.yaml` fija
`SPRING_JPA_HIBERNATE_DDL_AUTO: update`. No hay Flyway ni Liquibase. `README.md:114` ya lo reconoce.

**Por qué implementarlo.** `update` en producción implica que Hibernate decide el DDL en el arranque.
Consecuencias reales: (a) una migración destructiva puede ejecutarse sin revisión; (b) no hay
historial de cambios de esquema ni forma de reproducirlos; (c) el arranque puede fallar por un
`ALTER TABLE` bloqueado; (d) dos instancias arrancando a la vez pueden competir por el DDL. Y al
incorporar B-01 (`Usuario`) y B-02 (modificaciones), el esquema cambia de forma no trivial.

**Qué implica.** Adoptar Flyway (o Liquibase) con scripts versionados en
`src/main/resources/db/migration/`, desactivar `ddl-auto` en producción (`validate`), y mantener
`update` solo en desarrollo.

**Criterio de aceptación.** Una base de datos vacía se reconstruye con solo `mvn test` o el arranque
de la app; un entorno existente se actualiza de forma versionada y repetible.

---

### B-14 — El resumen de cocina no tiene respaldo de papel

**Estado actual.** `GET /api/v1/pedidos/resumen-cocina` (`PedidoController:43`) entrega el agregado.
No hay ningún endpoint que devuelva el detalle imprimible de un pedido.

**Por qué implementarlo.** Es una consecuencia de B-01 y B-02: en cuanto los pedidos puedan estar
asociados a un usuario formal, la cocina necesitará poder imprimir el ticket de una comanda
concreta con nombre, hora de retiro y observaciones (que incluyen restricciones alimentarias). El
"resumen" no sirve para eso; sirve para saber **cuánto** preparar en total, no **qué** préparer para
un pedido con la hora de retiro más próxima.

**Criterio de aceptación.** Existe un endpoint que devuelve el detalle completo de un pedido en
formato de ticket.

---

## 7. Posibles mejoras (más allá de lo que exige el manual)

Estas no son breaches del manual: son mejoras que el proyecto no necesita para cumplir, pero que
reducen riesgo operativo o mejoran el producto. Están ordenadas por relación esfuerzo/beneficio.

### 7.1 Seguridad

| # | Mejora | Por qué |
|---|---|---|
| M-01 | **Rate limiting** en los endpoints públicos (`POST /pedidos`, `/seguimiento/{codigo}`) | Hoy `GET /api/v1/pedidos/seguimiento/{codigo}` permite enumerar comandas: el código son 4 caracteres hexadecimales (16.777.216 combinaciones) y no hay límite de intentos. Un atacante puede probarlos en bucle. |
| M-02 | **Rotación y caducidad de tokens** (JWT con `exp` corto + refresh) | Evita sesiones eternalmente válidas. |
| M-03 | **Cabeceras de seguridad** (Helmet: CSP, HSTS, `X-Content-Type-Options`) | Configuración de endurecimiento por defecto si se agrega un panel web. |
| M-04 | **Hash de contraseña con coste ajustable** y política de contraseñas | `BCryptPasswordEncoder` con strength configurable por entorno. |
| M-05 | **Auditoría de accesos** (quién cambió qué estado y cuándo) | Trazabilidad para las operaciones de cocina y para los reclamos. |

### 7.2 Operación y despliegue

| # | Mejora | Por qué |
|---|---|---|
| M-06 | **Perfiles de Spring** (`application-dev.yml`, `application-prod.yml`) | Hoy todo se controla por variables de entorno sueltas; los perfiles permiten diferenciar el comportamiento (timeouts, logging, `ddl-auto`) de forma legible. |
| M-07 | **Sincronización automática** con `@Scheduled` (además del POST manual) | Hoy `POST /api/v1/menu/sincronizar` depende de que alguien lo llame. El manual incluso sugiere "un servicio lee la API de Google Sheets" con una lectura automática. Programar la sincronización diaria garantiza que `menu_diario` exista al abrir el día. |
| M-08 | **Health checks de Actuator** en `compose.yaml` | Hoy el `depends_on` espera al healthcheck de MySQL, pero el backend no expone el suyo. Un orquestador no puede distinguir "arrancando" de "roto". |
| M-09 | **Límite de memoria y GC explícitos** | El `Dockerfile` ya usa `-XX:MaxRAMPercentage=50`, lo que es correcto. Documentar el perfil de memoria por tamaño de instancia EC2. |
| M-10 | **Estrategia de rollback** y versionado de imágenes | `compose.yaml` usa la etiqueta `casajojo-backend:local`; conviene taguear por versión/git SHA. |

### 7.3 Resiliencia de la integración con Sheets

| # | Mejora | Por qué |
|---|---|---|
| M-11 | **Reintentos con backoff exponencial** ante fallos de la API de Google | Un 429 o un 5xx transitorio de Google aborta hoy la sincronización completa (`SincronizacionException` → 502). |
| M-12 | **Circuit breaker** (Resilience4j) | Evita martillar la API de Sheets cuando está caída y agota su cuota. |
| M-13 | **Alerta de sincronización fallida** (métrica + log estructurado) | Si la sincronización falla a las 8:00 y nadie mira el log, el día arranca sin menú y **todos** los pedidos fallan con 422. Es el mayor riesgo operativo silencioso del sistema actual. |
| M-14 | **Validación del Sheet antes de escribir** (dry-run) | Hoy el parser ignora filas inválidas y las cuenta en `filasIgnoradas` (`:76`); un dry-run expondría el problema antes de tocar la BD. |

### 7.4 Rendimiento

| # | Mejora | Por qué |
|---|---|---|
| M-15 | **Caché en memoria del menú vigente** (Caffeine, TTL 1–5 min) | `GET /api/v1/menu` golpea la BD en cada llamada; con muchos clientes simultáneos es el endpoint de mayor tráfico. TTL corto mantiene la coherencia con el stock. |
| M-16 | **Índices compuestos** (`(estado, hora_retiro)`, `(fecha)`) | Acelera la cola de cocina y el resumen a medida que crezca la tabla. |
| M-17 | **Evitar N+1 en detalles** (`@EntityGraph` o consulta con `join fetch`) | `PedidoResponse` recorre `p.getDetalles()`; con `open-in-view=false` se traduce en una consulta por pedido. |
| M-18 | **Proyección DTO en las consultas de listado** | Cargar solo las columnas necesarias para listados largos. |

### 7.5 Calidad y pruebas

| # | Mejora | Por qué |
|---|---|---|
| M-19 | **Prueba de concurrencia real** sobre el descuento de stock | El bloqueo pesimista está implementado pero **nunca se ha probado bajo concurrencia**. Es el punto más frágil del sistema y el que más merece una prueba con hilos reales contra MySQL. |
| M-20 | **Pruebas de contrato con la App Android** (JSON de referencia) | Fija el formato exacto que consume el cliente Kotlin. |
| M-21 | **Testcontainers con MySQL real** en vez de H2 | H2 en modo MySQL no reproduce fielmente el comportamiento de bloqueos `SELECT ... FOR UPDATE`; las pruebas actuales podrían pasar en H2 y fallar en MySQL. |
| M-22 | **JaCoCo + umbral de cobertura** en CI | Hace verificable la cobertura en el tiempo. |
| M-23 | **Pipeline de CI** (build + test + lint + imagen) | Hoy no hay configuración de CI en el repositorio. |

### 7.6 Observabilidad

| # | Mejora | Por qué |
|---|---|---|
| M-24 | **Actuator + Micrometer + Prometheus** | Métricas de pedidos/día, tasa de rechazo por 409/422, duración de la sincronización. |
| M-25 | **Trazas distribuidas** (Micrometer Tracing / OpenTelemetry) | Correlacionar la app, el backend y la llamada a Sheets. |
| M-26 | **Identificador de correlación por request** | Permite que el error que ve el cliente y el log del servidor se cruzen. |
| M-27 | **Logs estructurados en JSON** | Búsqueda y agregación mucho más simples en producción. |

### 7.7 Producto y negocio

| # | Mejora | Por qué |
|---|---|---|
| M-28 | **Clave de idempotencia en `POST /pedidos`** | Si la app reintenta por timeout de red, hoy se pueden crear **dos pedidos** con el mismo contenido. Una `Idempotency-Key` evita el doble pedido. |
| M-29 | **Expiry de pedidos no retirados** (job diario) | Los pedidos `LISTO_PARA_RETIRO` que nadie recoge ensucian la cola y el inventario. |
| M-30 | **Historial de cambios de estado** (tabla de auditoría) | Hoy `Pedido.estado` es un campo sobrescrito: no queda rastro de cuándo pasó a `EN_PREPARACION`. |
| M-31 | **Métricas de negocio**: ticket promedio, tasa de conversión, horas pico | Orienta la operación del restaurante. |
| M-32 | **Reserva de stock para el pedido** | Hoy el stock se descuenta al crear el pedido; con M-29, un pedido expirado debería reponerlo automáticamente. |
| M-33 | **Múltiples puntos de retiro / salesperson** | Extensión natural del modelo de `hora_retiro` cuando el negocio crezca. |

---

## 8. Plan por fases

El orden no es arbitrario: cada fase deja el sistema en un estado coherente y verificable, y las
fases 1 y 2 son las que bloquean el despliegue real.

| Fase | Contenido | Depende de | Esfuerzo | Salida |
|---|---|---|---|---|
| **F0** | Higiene: B-10 (README vs config), B-11 (env var del Sheet), B-13 (migraciones), B-12 (verificar `PrecioExtra`), B-06 (catch-all) + test | — | ~4 h | Arranque reproducible, contrato de error uniforme |
| **F1** | **B-01** completo: `Usuario`, `UsuarioRepository`, `UsuarioService(Impl)`, `AuthController`, `LoginRequest`/`RegistroRequest`, `RolUsuario`, `TipoIdentificacion`, `SecurityConfig` (BCrypt), reglas por rol | F0 | 2–3 días | API con autenticación y autorización |
| **F2** | **B-02** completo: entidad de modificación, `ModificacionPedidoService(Impl)`, `SolicitarModificacionRequest`, endpoints, reajuste de stock y recálculo de total transaccionales | F1 | 1–1,5 días | Módulo 4 del manual cerrado |
| **F3** | B-05 (`CategoriaItem`), B-09 (agrupación por id), B-08 (paginación), B-03 (`OpenApiConfig`), B-04 (`CorsConfig`) | F1 | ~1 día | Contrato más limpio y descubrible |
| **F4** | B-07: tests `@WebMvcTest` de todos los controllers, de B-01 y B-02 | F1, F2 | ~1 día | Contrato HTTP cubierto |
| **F5** | Mejoras de la §7 priorizadas por el equipo (M-19, M-13, M-08, M-28, M-15…) | — | variable | Robustez operativa |

### 8.1 Ruta crítica

```
F0 ──► F1 ──► F2 ──► F4
        │
        └──► F3
```

**F1 es el punto de no retorno del proyecto**: hasta que exista autenticación, el backend no puede
exponerse fuera de una red de confianza, y el `README.md:126` ya lo dice.

### 8.2 Esfuerzo total estimado

| Concepto | Esfuerzo |
|---|---|
| Brechas del manual (F0–F4) | **5–7 días** |
| Mejoras adicionales (§7) | 3–10 días según prioridad |

---

## 9. Matriz de trazabilidad — clases exigidas por el manual (§3)

| Paquete | Clase exigida | Estado | Dónde está o dónde iría |
|---|---|---|---|
| `config/` | SecurityConfig | ❌ | Fase F1 |
| `config/` | OpenApiConfig | ❌ | Fase F3 |
| `config/` | CorsConfig | ❌ | Fase F3 |
| `enums/` | EstadoPedido | ✅ | `enums/EstadoPedido.java` |
| `enums/` | CategoriaItem | ❌ | Fase F3 |
| `enums/` | RolUsuario | ❌ | Fase F1 |
| `enums/` | TipoIdentificacion | ❌ | Fase F1 |
| `entity/` | Usuario | ❌ | Fase F1 |
| `entity/` | Pedido | ✅ | `entity/Pedido.java` |
| `entity/` | DetallePedido | ✅ | `entity/DetallePedido.java` |
| `entity/` | ItemMenu | ✅ | `entity/ItemMenu.java` |
| `entity/` | MenuDiario | ✅ | `entity/MenuDiario.java` |
| `dto/request/` | CrearPedidoRequest | ✅ | `dto/request/CrearPedidoRequest.java` |
| `dto/request/` | LoginRequest | ❌ | Fase F1 |
| `dto/request/` | SolicitarModificacionRequest | ❌ | Fase F2 |
| `dto/response/` | PedidoResponse | ✅ | `dto/response/PedidoResponse.java` |
| `dto/response/` | MenuDiarioResponse | ✅ | `dto/response/MenuDiarioResponse.java` |
| `dto/response/` | ResumenCocinaResponse | ✅ | `dto/response/ResumenCocinaResponse.java` |
| `repository/` | UsuarioRepository | ❌ | Fase F1 |
| `repository/` | PedidoRepository | ✅ | `repository/PedidoRepository.java` |
| `repository/` | ItemMenuRepository | ✅ | `repository/ItemMenuRepository.java` |
| `service/` | UsuarioService | ❌ | Fase F1 |
| `service/` | MenuService | ✅ | `service/MenuService.java` |
| `service/` | PedidoService | ✅ | `service/PedidoService.java` |
| `service/` | ModificacionPedidoService | ❌ | Fase F2 |
| `service/impl/` | UsuarioServiceImpl | ❌ | Fase F1 |
| `service/impl/` | MenuServiceImpl | ✅ | `service/impl/MenuServiceImpl.java` |
| `service/impl/` | PedidoServiceImpl | ✅ | `service/impl/PedidoServiceImpl.java` |
| `controller/` | AuthController | ❌ | Fase F1 |
| `controller/` | MenuController | ✅ | `controller/MenuController.java` |
| `controller/` | PedidoController | ✅ | `controller/PedidoController.java` |
| `exception/` | GlobalExceptionHandler | ✅ | `exception/GlobalExceptionHandler.java` |
| `exception/` | BusinessRuleException | ✅ | `exception/BusinessRuleException.java` |

**Total: 20 presentes · 13 ausentes**

---

## 10. Cómo verificar el avance

```powershell
# Suite completa (debe seguir en verde tras cada fase)
.\mvnw.cmd clean verify

# Contexto y aplicación (levanta con H2, sin MySQL)
.\mvnw.cmd -B test
```

Criterios de cierre por fase:

- **F0**: la app arranca con `docker compose up` siguiendo solo el README; ningún error devuelve
  una forma distinta de `{"error": "..."}`; el precio de un ítem del Sheet real coincide con el
  almacenado.
- **F1**: las 3 modalidades de cliente funcionan; un pedido ajeno no es visible para un cliente; los
  endpoints de cocina exigen rol; 401/403 correctos.
- **F2**: aprobar una modificación ajusta stock y total de forma atómica; una solicitud sobre un
  pedido no `RECIBIDO` se rechaza.
- **F3**: existe OpenAPI; el resumen separa homónimos; el listado pagina.
- **F4**: todo endpoint de la API tiene al menos un test.

---

## 11. Resumen en una línea

El proyecto implementa **los dos módulos operativos del manual (Menú/Google Sheets y Pedidos/Cocina)
de forma fiel, transaccional y bien probada**, pero le faltan **los dos módulos de identidad y de
ajuste (Usuario y ModificaciónPedido)** y, con ellos, **la capacidad de protegerse**. El primer
trabajo no es escribir código nuevo: es **implementar el Módulo 1**, porque mientras la API no tenga
autenticación, todo lo demás que se construya encima heredará esa debilidad.

---

*Documento generado el 2026-10-21 sobre el estado del repositorio en ese momento. Las referencias
`archivo:línea` apuntan al código de la revisión; verifícalas tras cada commit.*
