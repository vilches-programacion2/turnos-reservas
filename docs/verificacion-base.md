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
