# turnos-reservas

Backend de usuarios y reservas: Java 25, Spring Boot, PostgreSQL y Flyway.
Implementa registro, autenticación con JWT RS256 e integración HTTP con catálogo.
El flujo de reservas sigue en desarrollo.

## Ejecutar

Requisitos: Docker Compose y OpenSSL. Para desarrollo con Maven: JDK 25.

```bash
cp .env.example .env
# Configurar DB_PASSWORD en .env.
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/private.pem
openssl pkey -in secrets/private.pem -pubout -out secrets/public.pem
```

Conservar las claves entre ejecuciones. Compose monta `secrets/` como solo
lectura; el usuario del contenedor (`10001:10001`) necesita leer la privada y
acceder al directorio. Configurar grupo y permisos acordes al entorno, sin hacer
la privada legible para todos. `.env` y `secrets/` están excluidos de Git.

```bash
docker compose up --build -d
curl --fail http://localhost:8081/actuator/health/readiness
docker compose down
```

El volumen conserva los datos de PostgreSQL. Variables de configuración en
[.env.example](.env.example). Al ejecutar Java en el host, proporcionar las
variables de entorno y usar rutas `file:` absolutas del host para las claves;
Spring no carga `.env` automáticamente.

## API disponible

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/register` | Público, registro de usuario. |
| POST | `/api/authenticate` | Público, `username` y `password`; devuelve `id_token`. |
| GET | `/api/account` | JWT Bearer con `ROLE_USER`; devuelve la cuenta propia. |
| GET | `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness` | Público. |

## Integración con catálogo

Reservas emite el JWT del usuario con las audiencias `JWT_AUDIENCE` y
`JWT_CATALOG_AUDIENCE` (por defecto, `turnos-reservas` y `turnos-catalogo`).
Reenvía exactamente ese token desde la autenticación validada por Spring;
Catálogo valida con la misma clave pública, emisor, audiencia propia y `ROLE_USER`.
Los tokens anteriores solo para Reservas requieren un nuevo login para consultar
Catálogo. El JWT técnico de cátedra es independiente: nunca se usa en estas
consultas ni se entrega a Android. Su integración corresponde a RES-06.

Configurar `CATALOGO_BASE_URL` con una URL alcanzable desde Reservas. Con ambos
procesos en el host puede ser `http://localhost:8082`; entre contenedores se
necesita conectividad de red y el nombre/puerto del servicio, no localhost.
Los timeouts configurables son 3s de conexión y 5s de lectura por defecto.
No se siguen redirecciones ni se reintentan automáticamente las consultas.

Los puertos internos permiten consultar profesional y agenda mediante
`GET /api/professionals/{id}` y `GET /api/professionals/{id}/schedule` de Catálogo.
No se agrega un proxy HTTP público ni se comparte código, base o tablas.
La agenda conserva la versión aplicada por Catálogo: su inicialización y
sincronización corresponden a CAT-05/CAT-06, y aún no representa turnos libres.
Los errores externos bloquean la consulta dependiente sin inventar datos; login
y cuenta propia siguen disponibles. Los logs de fallo incluyen operación,
profesional, usuario autenticado y código, sin tokens ni cuerpos completos.

## Verificar

Con JDK 25 y Docker activos:

```bash
./mvnw verify
```

Las pruebas usan PostgreSQL real con Testcontainers y generan claves temporales.
La integración HTTP se prueba con respuestas simuladas y servidores locales,
incluyendo propagación del JWT, errores, timeout, redirecciones y operación local
cuando Catálogo no está disponible. No requieren credenciales ni VPN de cátedra.
Para el entorno Java aislado opcional: `./scripts/setup-java-env.sh /ruta/al/jdk-25`
y después `./mvnw-local verify`.

Trabajo por issue en ramas con PR a `dev`; promoción por PR a `test` y `main`.
Licencia [GPL-3.0](LICENSE).
