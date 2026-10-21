# Contratos base de `turnos-reservas`

Este documento separa los contratos externos definidos por la cátedra de las
decisiones internas que se implementarán en este backend. Es documentación de
diseño para el hito **Base y contratos**; en esta etapa se inicia RES-01 y los
endpoints todavía no están implementados.

## 1. Límites y arquitectura

`turnos-reservas` es un servicio Java/Spring Boot con arquitectura hexagonal y
organización por funcionalidades. Cada funcionalidad tendrá dominio,
puertos de entrada y salida, casos de uso, adaptadores de persistencia y
adaptadores web o de mensajería.

El servicio es propietario de sus usuarios, procesos de reserva, reservas,
estados e idempotencia. Tendrá una base de datos servidor y migraciones
propias. No leerá tablas del catálogo ni mantendrá una réplica consultable del
catálogo. La información descriptiva histórica necesaria para una reserva se
conservará en sus propios registros.

La persistencia principal no será H2, SQLite ni una base embebida. Las
credenciales, URLs y nombres de infraestructura se obtendrán de configuración
externa; no deben aparecer en el código, documentación pública ni logs.

## 2. Contratos externos confirmados

La referencia de integración entregada por la cátedra es la fuente de verdad
para REST, Redis y Kafka. Los dos backends usan la misma cuenta técnica y el
JWT técnico de cátedra. Ese token es distinto del JWT de los usuarios finales
y nunca se envía a la aplicación Android.

Convenciones comunes:

- REST protegido: `Authorization: Bearer <jwt-técnico>`.
- Fechas: `yyyy-MM-dd`; horas locales: `HH:mm` o `HH:mm:ss`.
- Instantes: ISO-8601 en UTC.
- JSON en `camelCase`.
- Errores REST con `application/problem+json` y código funcional estable.
- JWT, contraseñas y configuración sensible no se registran ni se versionan.
- Kafka entrega al menos una vez; los consumidores deben tolerar duplicados e
  ignorar campos opcionales desconocidos de `schemaVersion: 1`.

### Cuenta técnica

La cuenta técnica se crea una vez mediante `POST /api/student/register`; un
nuevo JWT técnico se obtiene mediante `POST /api/authenticate`. La integración vigente se
consulta con `GET /api/student/integration`. Registro y login no requieren el
JWT técnico; el resto de operaciones sí requiere `ROLE_STUDENT_CLIENT`.

La respuesta de integración entrega, entre otros valores, `groupId`, acceso a
Redis, `kafkaBootstrapServers`, `kafkaConsumerGroupId` y los tres topics. El
`groupId` se obtiene del token/configuración entregados por la cátedra; el
cliente no puede elegirlo en cada operación.

### REST de reservas consumido desde la cátedra

El adaptador externo deberá conocer estos recursos, todos autenticados con la
cuenta técnica:

| Operación | Resultado contractual |
| --- | --- |
| `GET /api/appointment-occupancies` | Ocupaciones `CONFIRMED` y `HELD` para profesional y rango de fechas. |
| `POST /api/appointment-holds` | Crea un hold temporal y devuelve `holdId`, `reservationProcessId`, estado y `expiresAt`. |
| `POST /api/appointment-holds/{holdId}/confirm` | Confirma inicialmente el hold y devuelve `202 WAITING_FOR_PHONE`. |
| `GET /api/appointments` | Lista paginada de reservas de la integración técnica. |
| `POST /api/appointments/{reservationProcessId}/cancel` | Cancela una reserva; repetir una cancelación debe ser idempotente. |

Los holds son propiedad de la cátedra: su vencimiento real es `expiresAt` y
una copia local nunca extiende su TTL. Antes de crear una operación de reserva
se deberá consultar información vigente del catálogo mediante el contrato
correspondiente. Una indisponibilidad externa no debe producir una reserva
local confirmada de manera falsa.

### Redis y sincronización del catálogo

El servicio de reservas no usa Redis como base propia ni como réplica del
catálogo. Cuando su caso de uso necesite datos vigentes, los obtendrá mediante
el puerto/adaptador definido para el servicio de catálogo y las ocupaciones de
la cátedra.

Como referencia del contrato de sincronización del otro backend: Redis expone
lectura en `catedra:sync:*`; las claves de entidades contienen el estado actual
y `catedra:sync:changes:{version}` contiene únicamente IDs afectados. Un
cambio incremental debe localizar esos IDs y leer solo sus valores actuales,
nunca descargar la base completa por cada cambio. Si la versión local quedó
fuera de la ventana retenida, o no puede reconstruirse de forma segura por
haber estado mucho tiempo offline, se solicita el snapshot completo. Los holds
`catedra:holds:*` son internos y no forman parte del acceso contractual.

### Kafka

Los nombres concretos de topics y el consumer group se leen de la integración
entregada por la cátedra:

| Topic | Dirección | Uso |
| --- | --- | --- |
| `alumnos.turnos.acciones.{groupId}` | Cátedra → backend | `AdditionalInformationRequested`, confirmaciones, rechazos, expiraciones, procesos inválidos y cancelaciones. |
| `catedra.turnos.telefono.{groupId}` | Backend → cátedra | `AdditionalInformationSubmitted`. |

Los eventos de un mismo proceso usan como message key el
`reservationProcessId` y deben procesarse con idempotencia basada en
`eventId`. La respuesta de teléfono conserva el `requestEventId` recibido.
`AdditionalInformationRejected` permite corregir el teléfono hasta el
vencimiento: no es un rechazo terminal. `CONFIRMED → CANCELLED` es una
transición válida. Los eventos tardíos no deben reabrir procesos expirados.
Las respuestas
perdidas, duplicadas o fuera de orden se resolverán con estado persistido,
reintentos limitados y reconciliación antes de repetir efectos externos.

## 3. Contratos internos propuestos

Estos contratos pertenecen al equipo y pueden ajustarse antes de cerrar el
anexo técnico interno. Se expondrán como puertos de dominio, sin hacer que el
dominio dependa de Spring, JPA, Jackson o clientes concretos.

### Usuarios y sesión

- `RegistrarUsuario`: recibe `login`, `password`, `firstName`, `lastName`,
  `email`, `imageUrl` opcional y `langKey`; el backend genera ID, auditoría,
  autoridades y deja el usuario activado.
- `AutenticarUsuario`: valida credenciales y emite un JWT de usuario,
  independiente del JWT técnico.
- `ObtenerUsuarioAutenticado`: deriva la identidad del contexto autenticado;
  ningún caso de uso confiará en un ID de usuario enviado por el cliente.

El modelo observable debe ser compatible con JHipster. Las contraseñas se
almacenan con hashing y nunca se devuelven.

### Reservas y propiedad

Los procesos y reservas conservarán el `userId` local junto con sus
identificadores externos (`holdId`, `reservationProcessId` y, cuando exista,
`reservationId`). Todos los puertos de consulta, confirmación y cancelación
reciben la identidad autenticada y aplican propiedad local. Un usuario no
puede consultar ni cancelar procesos de otra cuenta, aunque comparta la misma
cuenta técnica ante la cátedra.

### Integración

Se prevén puertos separados para:

- consultar profesionales y horarios vigentes;
- consultar ocupaciones;
- crear y confirmar holds;
- publicar información adicional por Kafka;
- consumir resultados de procesos;
- reconciliar un proceso por sus identificadores.

Los adaptadores HTTP y Kafka traducirán DTO externos a modelos internos y
mapearán errores por código/estado. El dominio no conocerá URLs, topics,
headers, JWT ni entidades JPA.

## 4. Decisiones pendientes

- Confirmar con la cátedra la URL final y las credenciales de integración por
  un canal seguro.
- PostgreSQL 17 es el motor elegido; RES-02 definirá las migraciones de los
  datos propios dentro de su base exclusiva.
- Cerrar los DTO y endpoints internos entre `turnos-reservas`,
  `turnos-catalogo` y la aplicación KMP.
- Definir la máquina de estados completa y sus transiciones inválidas antes de
  implementar el flujo REST + Kafka.

Estas decisiones no habilitan a publicar secretos ni direcciones privadas.
