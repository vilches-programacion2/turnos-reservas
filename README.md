# turnos-reservas

Servicio Java y Spring Boot para autenticación de usuarios, disponibilidad y gestión de reservas.

## Estado
Repositorio inicial de planificación. Las funcionalidades del backlog todavía no están implementadas.

Este repositorio alojará el Docker Compose general y el diagrama de arquitectura. Usuarios y autenticación pertenecen a este servicio.

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
Confirmar endpoints, topics, claves, payloads y validaciones al recibir el anexo.

## Licencia
GNU General Public License v3.0. Ver [LICENSE](LICENSE).

## Integración a main
Los cambios a `main` deben entrar mediante pull request; no se permiten pushes directos.
