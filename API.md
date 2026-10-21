# API de CasaJoJoApp (backend)

URL base: `http://<IP-DE-LA-EC2>:8080`. Todo es JSON. Los errores responden `{"error": "mensaje"}`.

| Código | Cuándo |
|---|---|
| 400 | Datos mal formados (campo faltante, hora con formato inválido, ítem inexistente, estado no válido) |
| 404 | El pedido o el menú de hoy no existen |
| 409 | Choca con el estado actual (stock insuficiente, transición de estado no permitida) |
| 422 | Viola una regla del negocio (menú cerrado, hora de retiro con menos de 20 minutos, ítem no disponible) |
| 502 | No se pudo leer el Google Sheet al sincronizar |

## Menú

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/api/v1/menu` | Menú de hoy desde MySQL (no consulta Google Sheets) |
| POST | `/api/v1/menu/sincronizar` | Lee el Sheet y actualiza precios, disponibilidad y stock del día |
| PATCH | `/api/v1/menu/estado` | Abre o cierra la recepción de pedidos. Cuerpo: `{"estado": "ABIERTO"}` o `{"estado": "CERRADO"}` |

Respuesta de `GET /api/v1/menu`:

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
| POST | `/api/v1/pedidos` | El cliente crea su pedido (201) |
| GET | `/api/v1/pedidos/{id}` | Consultar un pedido por id |
| GET | `/api/v1/pedidos/seguimiento/{codigo}` | Consultar por código de seguimiento (`CJ-98F2`) |
| GET | `/api/v1/pedidos?estado=RECIBIDO` | Listar pedidos (el filtro es opcional), por hora de retiro |
| GET | `/api/v1/pedidos/cola` | Cola de cocina: pedidos RECIBIDO y EN_PREPARACION, el retiro más próximo primero |
| GET | `/api/v1/pedidos/resumen-cocina` | Cantidad total a preparar de cada ítem |
| PATCH | `/api/v1/pedidos/{id}/estado` | Avanzar el estado. Cuerpo: `{"estado": "EN_PREPARACION"}` |

Estados, siempre de a uno: `RECIBIDO` → `EN_PREPARACION` → `LISTO_PARA_RETIRO` → `ENTREGADO`.

Cuerpo de `POST /api/v1/pedidos`:

```json
{
  "usuarioId": null,
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

- `horaRetiroSolicitada`: formato `HH:mm` (con dos dígitos), hora de Chile, al menos 20 minutos después de la hora actual.
- `observaciones` es donde el cliente escribe sus restricciones alimentarias. Es opcional.
- `itemMenuId` sale de `GET /api/v1/menu`. El servidor calcula los precios; la app no envía montos.

Respuesta (201):

```json
{
  "id": 482,
  "codigoSeguimiento": "CJ-98F2",
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

El backend no envía notificaciones push. La app guarda el `id` del pedido en Room y consulta
`GET /api/v1/pedidos/{id}` cada cierto tiempo; si el `estado` cambió, dispara la notificación local.
