# Verificación de RES-01

Validación local del 2026-10-17, rama `feature/1-base-hexagonal`.

- Maven Wrapper 3.9.11 y JDK 25.0.4.1, seleccionado mediante `JAVA_HOME`.
- `./mvnw compile`: correcto.
- `./mvnw clean verify`: correcto, recompilación desde cero y una prueba,
  sin errores, fallos ni omisiones.
- Testcontainers inicia PostgreSQL 17 real y el servidor HTTP de reservas.
  La prueba comprueba salud sin detalles internos, denegación de endpoints
  de negocio y diagnóstico, y readiness `503` con liveness `200` después
  de detener la base. Los errores de conexión de ese escenario son esperados.
- `docker compose config --quiet`: correcto con una clave ficticia de validación.

La ejecución utilizó la caché Maven local en modo offline; no necesitó acceso
a infraestructura ni credenciales de cátedra. No se cambió el Java global.

Esto verifica el bootstrap RES-01. No acredita registro, JWT, migraciones,
reservas ni integración con catálogo/cátedra, que todavía no están implementados.

## RES-02 y entorno aislado — 2026-10-17

- `.java-env/` contiene el JDK 25.0.4.1, Maven Wrapper 3.9.11 y su repositorio
  de dependencias; `git check-ignore` confirma que están excluidos de Git.
- `./mvnw-local -version` confirma que Java y Maven se ejecutan desde esa carpeta.
  `java -version` fuera del comando conserva la selección global Java 21.
- `bash -n mvnw-local scripts/setup-java-env.sh`: correcto.
- `./mvnw-local compile`: correcto, 15 fuentes compiladas con release 25.
- El primer intento offline detectó dependencias Flyway todavía no descargadas.
  Se completó su descarga en `.java-env/repository`.
- `./mvnw-local verify`: correcto; seis pruebas con PostgreSQL real, sin fallos,
  errores ni omisiones. Flyway aplicó V1 y Hibernate validó el esquema.

Se verificaron las restricciones de identidad, referencias y concurrencia,
además del arranque HTTP y sus controles de salud. La descripción anterior de
RES-01 es histórica; RES-02 sí incorpora migraciones. Registro, JWT y el flujo
funcional de reserva siguen pendientes.
