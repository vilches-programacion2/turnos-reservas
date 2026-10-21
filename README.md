# turnos-reservas

Servicio Java y Spring Boot para autenticación de usuarios, disponibilidad y gestión de reservas.

## Estado
Primera entrega de **RES-01 / Base y contratos**: bootstrap Java 25 y Spring
Boot 4.1.0, configuración externa, PostgreSQL, logs con Lombok y controles de
salud. Registro, JWT y reservas todavía no están implementados.

RES-02 agrega migraciones Flyway y adaptadores de persistencia de usuarios y
procesos, con propiedad local y control de actualizaciones concurrentes. Ver
[modelo de persistencia](docs/persistencia.md). El flujo de reservas y sus
endpoints todavía están pendientes.

Este repositorio alojará el Docker Compose general y el diagrama de arquitectura. Usuarios y autenticación pertenecen a este servicio.

## Ejecutar

Con Docker y Docker Compose:

```bash
cp .env.example .env
# Editar DB_PASSWORD en .env con una clave local.
docker compose up --build -d
curl --fail http://localhost:8081/actuator/health/readiness
docker compose logs -f reservas
docker compose down
```

La primera construcción descarga dependencias. El volumen conserva la base al
detener los contenedores. Los puertos se publican solo en localhost. Cambiar
DB_PASSWORD después de crear el volumen no modifica la clave ya almacenada
por PostgreSQL: debe rotarse también dentro de la base.

Para desarrollar en el entorno Java aislado, prepararlo una vez con un JDK 25
ya descargado (también puede provenir de SDKMAN):

```bash
./scripts/setup-java-env.sh /ruta/al/jdk-25
./mvnw-local -version
./mvnw-local compile
```

`.java-env/` está excluida de Git y contiene una copia del JDK, la distribución
Maven descargada por el Wrapper y el repositorio de dependencias. Las nuevas
descargas quedan ahí. `mvnw-local` configura Java y las cachés solo para ese
comando: no necesita activación ni modifica SDKMAN o el Java global. El entorno
ocupa disco local y se puede borrar para reconstruirlo; no contiene los datos
de PostgreSQL. Después de clonar en otra máquina hay que prepararlo de nuevo.
El Wrapper oficial `./mvnw` sigue disponible para CI y otros entornos.

Para ejecutar el servicio desde Java:

```bash
docker compose up -d reservas-db
export DB_URL=jdbc:postgresql://localhost:5433/reservas
export DB_USER=reservas
read -rs -p 'Clave de PostgreSQL: ' DB_PASSWORD
export DB_PASSWORD
./mvnw-local spring-boot:run
```

La ejecución Java usa el puerto 8080 por defecto; `SERVER_PORT` permite
cambiarlo. Spring no lee `.env` automáticamente. `DB_URL`, `DB_USER` y
`DB_PASSWORD` son obligatorias; no se incluyen credenciales de cátedra.

## Verificar

Con JDK 25 y Docker activos:

```bash
./mvnw-local verify
```

La prueba inicia PostgreSQL con Testcontainers, verifica el servicio HTTP,
el cierre de endpoints de negocio y el comportamiento de readiness/liveness
ante una caída real de la base. No omite la prueba si Docker está ausente.

Ver [arquitectura](docs/arquitectura.md) y
[contratos base](docs/contratos-base.md). No se requiere acceso a ZeroTier
para estas pruebas locales.

## Trabajo
- `dev`: integración cotidiana.
- `test`: validación de entregas.
- `main`: versión estable.

Crear ramas `feature/<numero>-<descripcion>` desde `dev` y abrir PR hacia `dev`. Promover conjuntos verificados a `test` y luego a `main`. Cerrar la issue al integrar en `dev` y cumplir sus criterios; las palabras automáticas de cierre pueden no ejecutarse hasta llegar a la rama predeterminada.

Ver [backlog](BACKLOG.md) y [guía de contribución](CONTRIBUTING.md).

## Restricciones
Exactamente dos backends Java/Spring Boot y una app KMP Android. Bases servidor con propiedad y migraciones separadas. El anexo técnico oficial define contratos externos. Sin datos médicos reales.

## Conventional Commits
Usar `<tipo>(<alcance>): <descripción concreta en español>`.
Tipos: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.
Ejemplos:
- `feat(catalogo): aplicar cambios incrementales por identificador de profesional`
- `fix(reservas): impedir la cancelación de turnos de otro usuario`
- `test(sync): cubrir recuperación tras perder la ventana incremental`
Evitar mensajes como `cambios`, `arreglos` o `update`. Agregar en el cuerpo motivos,
decisiones y referencia a la issue cuando ayuden. Usar también títulos de PR
descriptivos con este formato, especialmente al integrar mediante squash.

## Integración de cátedra
Se consumen los contratos que entreguen los profesores. La consigna menciona
REST (API académica), Redis (catálogo e historial) y Kafka (eventos).
No se asume que sean tres APIs HTTP independientes ni se inventa una integración adicional.
El anexo de integración recibido define endpoints, topics, claves y payloads;
su resumen está en `docs/contratos-base.md`. La conexión real se incorporará
en RES-06 con configuración privada.

## Licencia
GNU General Public License v3.0. Ver [LICENSE](LICENSE).

## Integración a main
Los cambios a `main` deben entrar mediante pull request; no se permiten pushes directos.
