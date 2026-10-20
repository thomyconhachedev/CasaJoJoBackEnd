# CasaJoJoApp Backend

Backend REST de CasaJoJoApp, desarrollado con Spring Boot y Java 21. Administra el menú diario, el inventario local y las comandas para el flujo de atención y preparación en cocina.

## Contenido del proyecto

- `src/main/java`: código del backend (controllers, services, repositories, entidades y DTOs).
- `src/test`: pruebas automatizadas.
- `Dockerfile`: construcción de la imagen del backend.
- `pom.xml` y `mvnw` / `mvnw.cmd`: configuración y wrapper de Maven.
- `API.md`: rutas REST, cuerpos de solicitud y ejemplos de respuesta.

## Modelo de datos

La aplicación define cuatro entidades JPA principales. Hibernate administra el esquema de la base de datos según la configuración de `spring.jpa.hibernate.ddl-auto`.

### 1. `menu_diario`

Representa el estado operativo del menú de una fecha.

| Campo | Descripción |
|---|---|
| `id` | Identificador generado. |
| `fecha` | Fecha del menú; es única y obligatoria. |
| `estado` | Estado del menú (`ABIERTO` o `CERRADO`). Por defecto, `ABIERTO`. |
| `ultima_sincronizacion` | Fecha y hora de la última sincronización registrada. |

Esta entidad controla si se aceptan pedidos para el día. No contiene las líneas de productos y actualmente no tiene una relación mediante clave foránea con `item_menu`.

### 2. `item_menu`

Mantiene en MySQL una copia local de los productos importados desde Google Sheets y sus existencias operativas.

| Campo | Descripción |
|---|---|
| `id` | Identificador generado del producto. |
| `codigo_externo` | Código del producto en la fuente externa; es único cuando tiene valor. |
| `categoria` | Categoría del producto. |
| `nombre` | Nombre del producto. |
| `descripcion` | Descripción opcional. |
| `precio` | Precio vigente del producto. |
| `stock_inicial` | Stock inicial del día. |
| `stock_actual` | Stock que queda disponible. |
| `disponible` | Indicador de disponibilidad. |

El backend consulta esta copia local al mostrar el menú y crear pedidos, en lugar de consultar Google Sheets en cada compra. La sincronización puede actualizar productos y eliminar los que ya no aparezcan en la fuente.

### 3. `pedido`

Guarda la cabecera de una comanda, con la información general del cliente y su preparación.

| Campo | Descripción |
|---|---|
| `id` | Identificador generado del pedido. |
| `codigo_seguimiento` | Código único para consultar el pedido. |
| `usuario_id` | Identificador opcional reservado para una futura integración con usuarios registrados; no existe una entidad `Usuario` en este proyecto. |
| `nombre_cliente` | Nombre del cliente. |
| `telefono_contacto` | Teléfono de contacto, cuando se proporciona. |
| `hora_retiro` | Hora solicitada para retirar el pedido. |
| `observaciones` | Notas del cliente, incluidas especificaciones o restricciones alimentarias. |
| `estado` | Estado de preparación del pedido. |
| `monto_total` | Total calculado por el backend. |
| `fecha_creacion` | Instante de creación del pedido. |

Los estados de pedido previstos siguen esta secuencia, avanzando de uno en uno: `RECIBIDO` → `EN_PREPARACION` → `LISTO_PARA_RETIRO` → `ENTREGADO`.

### 4. `pedido_detalle`

Guarda cada línea de producto que forma parte de un pedido.

| Campo | Descripción |
|---|---|
| `id` | Identificador generado de la línea. |
| `pedido_id` | Referencia obligatoria al pedido al que pertenece. |
| `item_menu_id` | Identificador del producto de origen, almacenado como dato y **sin clave foránea** a `item_menu`. |
| `nombre_item` | Nombre del producto tal como estaba al crear el pedido. |
| `cantidad` | Número de unidades solicitadas. |
| `precio_historico` | Precio unitario guardado en el momento de la compra. |

Guardar `nombre_item` y `precio_historico` permite mantener el detalle histórico aunque cambien los precios o la sincronización elimine un producto de `item_menu`. La relación con `pedido` sí está mapeada mediante una clave foránea.

## Relaciones y flujo de datos

- Un `pedido` puede tener varios registros en `pedido_detalle`.
- Cada `pedido_detalle` pertenece a un único `pedido`.
- `pedido_detalle.item_menu_id` identifica el producto que se pidió, pero no es una clave foránea. Esto permite conservar el historial aunque el producto se retire del menú local.
- `menu_diario` y `item_menu` son entidades independientes en el modelo actual: no tienen una relación mediante clave foránea.
- Al crear un pedido, el servidor obtiene los precios actuales desde el menú local, calcula el total, guarda el nombre y el precio histórico de cada línea y descuenta el stock.

## Alcance actual y funciones que no están implementadas

El modelo de datos cubre principalmente menú diario, catálogo e inventario, cabeceras de pedidos y sus detalles. En el código actual no hay una tabla/entidad de usuarios, autenticación y roles, módulo de solicitudes de modificación de pedidos ni módulo de pagos. `usuario_id` es solo un campo reservado y no implementa por sí mismo esas funciones.

Tampoco se conserva una fotografía completa de cada menú publicado por fecha: `item_menu` representa el catálogo operativo actual, mientras que las líneas de pedidos conservan el nombre y el precio utilizados en cada compra.

## Requisitos

- JDK 21 para ejecutar el proyecto y sus pruebas localmente.
- Docker Desktop con Docker Compose, si se desea ejecutar en contenedores.
- MySQL compatible con el controlador incluido en el proyecto.

## Ejecutar las pruebas en Windows

Desde PowerShell, en la carpeta raíz del proyecto:

```powershell
.\mvnw.cmd clean verify
```

## Configuración de base de datos

La configuración actual está en `src/main/resources/application.properties`. En el estado original del proyecto, usa MySQL en `localhost:3306`, base `casajojo`, usuario `root` y contraseña vacía. Es una configuración de desarrollo que debe cambiarse antes de compartir un entorno o desplegarlo: usa un usuario de base de datos con privilegios mínimos y entrega la URL, usuario y contraseña mediante variables de entorno o un gestor de secretos. No publiques credenciales reales en GitHub.

La propiedad `spring.jpa.hibernate.ddl-auto=update` permite que Hibernate intente actualizar el esquema al iniciar. Para un entorno de producción, conviene administrar los cambios de esquema mediante migraciones versionadas y revisadas.

La fuente del menú se configura mediante `menu.sheet.csv-url`. El backend sincroniza los datos de esa fuente hacia MySQL; para crear pedidos usa el inventario local.

## API

Consulta [`API.md`](API.md) para los endpoints disponibles, formatos JSON y ejemplos de uso.

## Docker y despliegue

El `Dockerfile` incluido construye y ejecuta el backend. Para usarlo con una base de datos en otro contenedor o en un servicio administrado, configura la dirección de MySQL para que el backend pueda alcanzarla; `localhost` dentro del contenedor se refiere al propio contenedor, no a otro servicio.

Antes de exponer la API a Internet, implementa y verifica autenticación y autorización para las operaciones administrativas y la consulta de datos de pedidos. La imagen Docker y las pruebas correctas no sustituyen esos controles.
