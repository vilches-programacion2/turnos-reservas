# Guía de contribución

1. Elegir una issue y revisar dependencias y bloqueos.
2. Crear una rama desde `dev`: `feature/<numero>-descripcion` o `fix/<numero>-descripcion`.
3. Implementar y verificar los criterios de aceptación; documentar cambios.
4. Abrir un PR hacia `dev` y vincular la issue por URL.
5. Revisar, integrar y cerrar la issue al cumplir los criterios.
6. Promover cambios mediante PR `dev` → `test` → `main`.

Estados del Project: Backlog → Listo para desarrollar → En progreso → En revisión → Hecho. No iniciar tareas bloqueadas por el anexo sin obtener el contrato oficial.

Pruebas backend acompañan cada funcionalidad. La prioridad ordena el trabajo y no reduce requisitos. Conservar commits incrementales durante el desarrollo.

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

## Logs backend
Usar Lombok `@Slf4j` para generar el logger SLF4J y emitir logs mediante `log.info`, `log.warn`, `log.error` y `log.debug`. Configurar niveles con Spring Boot. Registrar errores, correlación y progreso relevante, sin contraseñas, JWT, credenciales técnicas ni teléfonos completos. Lombok evita declarar el logger manualmente; no es un visor de logs.
