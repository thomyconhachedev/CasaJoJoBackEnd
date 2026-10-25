# API de CasaJoJoApp (backend)

URL base: `http://<IP-DE-LA-EC2>:8080`. Todo es JSON. Los errores responden `{"error": "mensaje"}`.

| Código | Cuándo |
|---|---|
| 400 | Datos mal formados (campo faltante, hora con formato inválido, ítem inexistente, estado no válido) |
| 401 | Falta el token, está vencido, o el usuario/contraseña son incorrectos |
| 403 | Hay sesión, pero el rol no alcanza (un USUARIO intentando usar el panel de cocina) |
| 404 | El pedido o el menú de hoy no existen |
| 409 | Choca con el estado actual (stock insuficiente, transición de estado no permitida, usuario repetido) |
| 422 | Viola una regla del negocio (menú cerrado, hora de retiro con menos de 20 minutos, ítem no disponible) |
| 502 | No se pudo leer el Google Sheet al sincronizar |

## Roles

| Rol | Cómo se obtiene | Pide | Ve el panel de cocina |
|---|---|---|---|
| INVITADO | Sin cuenta: la app simplemente no envía `Authorization` | Sí (`usuarioId` queda en `null`) | No |
| USUARIO | `POST /api/v1/auth/registro` o `/login` | Sí (el pedido queda asociado a su id) | No |
| ADMIN | Cuenta sembrada por el backend | Sí | Sí |

Con sesión, la app envía `Authorization: Bearer <token>`. El token dura 7 días.
**El invitado nunca envía `Authorization`.** Un token inválido o vencido se trata como "sin sesión".

## Quién puede llamar a qué

| Ruta | Acceso |
|---|---|
| `GET /api/v1/menu`, `POST /api/v1/menu/sincronizar` | Público |
| `POST /api/v1/auth/registro`, `POST /api/v1/auth/login` | Público |
| `POST /api/v1/pedidos` | Público (invitado) o con sesión (asocia el usuario) |
| `GET /api/v1/pedidos/seguimiento/{codigo}` | Público |
| `GET /api/v1/auth/me` | Cualquier usuario con sesión |
| `GET /api/v1/pedidos`, `/cola`, `/resumen-cocina`, `/{id}` | Solo ADMIN |
| `PATCH /api/v1/pedidos/{id}/estado`, `PATCH /api/v1/menu/estado` | Solo ADMIN |

## Auth

| Método | Ruta | Para qué |
|---|---|---|
| POST | `/api/v1/auth/registro` | Crea un USUARIO (201). Mínimos: usuario 3 caracteres, contraseña 6 |
| POST | `/api/v1/auth/login` | Devuelve el token (200). Si falla: 401 con el mismo mensaje exista o no el usuario |
| GET | `/api/v1/auth/me` | Datos de la sesión actual |

Cuerpo de registro: `{"username": "ana", "password": "secreto123", "nombre": "Ana Pérez", "telefono": "+56912345678"}`
(`nombre` y `telefono` son opcionales). Cuerpo de login: `{"username": "ana", "password": "secreto123"}`.
Los nombres de usuario no distinguen mayúsculas: se guardan en minúsculas.

Respuesta de registro, login y `/me` (nunca incluye la contraseña):

```json
{ "token": "eyJhbGciOi...", "id": 7, "username": "ana", "rol": "USUARIO",
  "nombre": "Ana Pérez", "telefono": "+56912345678" }
```

## Menú

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/api/v1/menu` | Menú de hoy desde MySQL (no consulta Google Sheets) |
| POST | `/api/v1/menu/sincronizar` | Lee el Sheet y actualiza precios, disponibilidad y stock del día |
| PATCH | `/api/v1/menu/estado` | (ADMIN) Abre o cierra la recepción de pedidos: `{"estado": "CERRADO"}` o `{"estado": "ABIERTO"}` |

```json
{
  "fecha": "2026-10-19",
  "estado": "ABIERTO",
  "items": [
    { "id": 12, "categoria": "PROTEINA", "nombre": "Pollo al Jugo", "descripcion": "Pechuga jugosa",
      "precio": 3500.00, "stock": 8, "disponible": true }
  ]
}
```

`disponible` es `false` si el Sheet lo marca como no disponible o si el stock llegó a 0.

## Pedidos

| Método | Ruta | Para qué |
|---|---|---|
| POST | `/api/v1/pedidos` | Crear un pedido (201) |
| GET | `/api/v1/pedidos/seguimiento/{codigo}` | Seguir un pedido con su código (`CJ-98F2`), con o sin sesión |
| GET | `/api/v1/pedidos/{id}` | (ADMIN) Consultar por id |
| GET | `/api/v1/pedidos?estado=RECIBIDO` | (ADMIN) Listar (el filtro es opcional), por hora de retiro |
| GET | `/api/v1/pedidos/cola` | (ADMIN) Cola de cocina: RECIBIDO y EN_PREPARACION, el retiro más próximo primero |
| GET | `/api/v1/pedidos/resumen-cocina` | (ADMIN) Cantidad total a preparar de cada ítem |
| PATCH | `/api/v1/pedidos/{id}/estado` | (ADMIN) Avanzar el estado: `{"estado": "EN_PREPARACION"}` |

Estados, siempre de a uno: `RECIBIDO` → `EN_PREPARACION` → `LISTO_PARA_RETIRO` → `ENTREGADO`.

Cuerpo de `POST /api/v1/pedidos`:

```json
{
  "nombreCliente": "Juan Pérez",
  "telefonoContacto": "+56912345678",
  "horaRetiroSolicitada": "13:30",
  "observaciones": "Sin sal. Alergia a los frutos secos.",
  "items": [
    { "itemMenuId": 12, "cantidad": 2 },
    { "itemMenuId": 45, "cantidad": 1 }
  ]
}
```

- `horaRetiroSolicitada`: formato `HH:mm` (dos dígitos), hora de Chile, al menos 20 minutos después de la hora actual.
- `observaciones`: restricciones alimentarias del cliente. Opcional.
- `itemMenuId` sale de `GET /api/v1/menu`. El servidor calcula los precios; la app no envía montos.
- `usuarioId`: si viene en el cuerpo **se ignora**. Con sesión se usa el id del token; sin sesión el pedido queda en `null`.

Respuesta (201):

```json
{
  "id": 482,
  "codigoSeguimiento": "CJ-98F2",
  "usuarioId": null,
  "estado": "RECIBIDO",
  "horaRetiro": "13:30",
  "nombreCliente": "Juan Pérez",
  "telefonoContacto": "+56912345678",
  "observaciones": "Sin sal. Alergia a los frutos secos.",
  "montoTotal": 8500.00,
  "fechaCreacion": "2026-10-19T13:15:00Z",
  "detalles": [
    { "itemMenuId": 12, "nombreItem": "Pollo al Jugo", "cantidad": 2, "precioUnitario": 3500.00 },
    { "itemMenuId": 45, "nombreItem": "Bebida 350ml", "cantidad": 1, "precioUnitario": 1500.00 }
  ]
}
```

## Notificaciones de cambio de estado

El backend no envía notificaciones push. La app guarda el `codigoSeguimiento` del pedido en Room y consulta
`GET /api/v1/pedidos/seguimiento/{codigo}` cada cierto tiempo (no necesita sesión, así que sirve también
para invitados); si el `estado` cambió, dispara la notificación local.

## Conectar la app Android

1. Retrofit: `AuthApi` con `registro`, `login` y `me`; guardar el `token`.
2. Un interceptor de OkHttp que agregue `Authorization: Bearer <token>` **solo si hay sesión**.
3. `crearPedido` sin cambios: con sesión viaja el header, sin sesión es un pedido de invitado.
4. Para el panel de cocina (solo ADMIN): `/pedidos/cola`, `/pedidos/resumen-cocina` y `PATCH /pedidos/{id}/estado`.

## Configuración (application.properties)

| Propiedad | Para qué | En la EC2 |
|---|---|---|
| `app.jwt.secret` | Clave que firma los tokens (mínimo 32 caracteres). **Cámbiala antes de publicar.** | `-e APP_JWT_SECRET=...` |
| `app.jwt.expiracion-dias` | Duración del token | `-e APP_JWT_EXPIRACION_DIAS=7` |
| `app.admin.username` / `app.admin.password` | Cuenta admin que se crea al arrancar si no existe | `-e APP_ADMIN_PASSWORD=...` |
| `app.zona-horaria` | Zona horaria del negocio (`America/Santiago`) | |
| `menu.stock-por-defecto` | Stock de cada ítem si el Sheet no trae columna `Stock` | |

El admin se crea solo la primera vez: cambiar `app.admin.password` después no modifica una cuenta ya creada.
