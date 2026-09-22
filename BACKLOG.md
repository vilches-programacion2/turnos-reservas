# Backlog inicial

Las claves son referencias estables, no números de issue de GitHub.

## RES-01 — Inicializar servicio de reservas con Java y Spring Boot

Hito: **Base y contratos** · Etiquetas: `infraestructura`, `prioridad:alta`

<!-- backlog:RES-01 -->
## Objetivo
Inicializar servicio de reservas con Java y Spring Boot.

## Criterios de aceptación
- [ ] El servicio compila e inicia con configuración externalizada y .env.example sin secretos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- Sin dependencias previas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.

## Logging obligatorio
- [ ] Configurar Lombok y usar `@Slf4j` en el backend.
- [ ] Verificar logs de inicio y errores con niveles configurables mediante Spring Boot y sin secretos.


## RES-02 — Crear persistencia y migraciones de usuarios y reservas

Hito: **Base y contratos** · Etiquetas: `infraestructura`, `prioridad:alta`

<!-- backlog:RES-02 -->
## Objetivo
Crear persistencia y migraciones de usuarios y reservas.

## Criterios de aceptación
- [ ] Mantener datos propios con migraciones independientes en base servidor; sin acceso al catálogo ni réplica para búsquedas; asociar procesos y reservas a su usuario.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-01

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-03 — Implementar registro compatible con JHipster

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-03 -->
## Objetivo
Implementar registro compatible con JHipster.

## Criterios de aceptación
- [ ] Validar login, password, firstName, lastName, email, imageUrl opcional y langKey según cátedra; aplicar hashing; activar inmediatamente. El backend controla ID, autoridades y auditoría; manejar duplicados.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-02
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-04 — Implementar login, JWT y autorización por usuario

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`

<!-- backlog:RES-04 -->
## Objetivo
Implementar login, JWT y autorización por usuario.

## Criterios de aceptación
- [ ] Emitir JWT al autenticar; validar firma, vigencia y permisos; tomar identidad del token y rechazar IDs de usuario arbitrarios; proteger endpoints no públicos.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-03

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-05 — Integrar servicio de catálogo mediante JWT

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`

<!-- backlog:RES-05 -->
## Objetivo
Integrar servicio de catálogo mediante JWT.

## Criterios de aceptación
- [ ] Consultar información vigente por contrato protegido; documentar propagación de JWT o token técnico y trazabilidad. Si catálogo no responde, impedir nuevas operaciones que lo requieran y mantener las posibles con datos propios.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-03
- RES-04

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-06 — Configurar integración REST y Kafka con cátedra

Hito: **Base y contratos** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-06 -->
## Objetivo
Configurar integración REST y Kafka con cátedra.

## Criterios de aceptación
- [ ] Usar la misma cuenta técnica del proyecto y configuración asignada según anexo; externalizar credenciales y definir timeouts y reintentos limitados.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-01
- CAT-04
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-07 — Calcular disponibilidad de turnos

Hito: **Reservas completas** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-07 -->
## Objetivo
Calcular disponibilidad de turnos.

## Criterios de aceptación
- [ ] Combinar horarios vigentes del catálogo, fecha elegida y ocupaciones centrales; excluir turnos ocupados y rechazar referencias o fechas inválidas.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-05
- RES-06
- CAT-08
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-08 — Implementar bloqueo temporal y confirmación inicial

Hito: **Reservas completas** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-08 -->
## Objetivo
Implementar bloqueo temporal y confirmación inicial.

## Criterios de aceptación
- [ ] Solicitar y confirmar hold por REST, conservar vencimiento y asociar proceso al usuario; manejar concurrencia y timeouts sin asumir que persistir el hold extiende su duración.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-07
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-09 — Implementar intercambio de información adicional por Kafka

Hito: **Reservas completas** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-09 -->
## Objetivo
Implementar intercambio de información adicional por Kafka.

## Criterios de aceptación
- [ ] Procesar AdditionalInformationRequested y enviar AdditionalInformationSubmitted con teléfono; correlacionar el proceso y manejar solicitudes duplicadas.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-08
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-10 — Implementar estados y resultados del proceso de reserva

Hito: **Reservas completas** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-10 -->
## Objetivo
Implementar estados y resultados del proceso de reserva.

## Criterios de aceptación
- [ ] Persistir confirmación, rechazo, expiración, proceso inválido y cancelación; converger entre REST y Kafka sin duplicar reservas ni retroceder estados finales.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-09
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-11 — Implementar consulta y cancelación de reservas propias

Hito: **Reservas completas** · Etiquetas: `funcionalidad`, `prioridad:alta`, `bloqueado`

<!-- backlog:RES-11 -->
## Objetivo
Implementar consulta y cancelación de reservas propias.

## Criterios de aceptación
- [ ] Consultar reservas de la cuenta técnica aplicando propiedad local; solo cancelar confirmadas propias; rechazar consulta o cancelación de otros usuarios.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-10
- RES-04
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-12 — Implementar idempotencia y recuperación de procesos

Hito: **Robustez y entrega** · Etiquetas: `funcionalidad`, `prioridad:media`, `bloqueado`

<!-- backlog:RES-12 -->
## Objetivo
Implementar idempotencia y recuperación de procesos.

## Criterios de aceptación
- [ ] Resolver duplicados, respuestas perdidas, mensajes fuera de orden, expiraciones y reinicios; persistir progreso y limitar reintentos sin estados irrecuperables.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-10
- RES-11
- **Bloqueado:** falta el anexo técnico oficial para concretar contratos o validaciones externas. No inventar endpoints, mensajes ni reglas.

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-13 — Preparar Docker Compose de la solución backend

Hito: **Base y contratos** · Etiquetas: `infraestructura`, `prioridad:alta`

<!-- backlog:RES-13 -->
## Objetivo
Preparar Docker Compose de la solución backend.

## Criterios de aceptación
- [ ] Levantar ambos servicios, bases servidor y dependencias locales; usar usuarios sin permisos cruzados y migraciones separadas; documentar clonación de ambos repos. Android se ejecuta fuera de contenedores.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- CAT-02
- RES-02

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-14 — Agregar pruebas automatizadas de reservas y seguridad

Hito: **Robustez y entrega** · Etiquetas: `pruebas`, `prioridad:media`

<!-- backlog:RES-14 -->
## Objetivo
Agregar pruebas automatizadas de reservas y seguridad.

## Criterios de aceptación
- [ ] Cubrir registro, login, disponibilidad, holds, resultados, idempotencia, recuperación y aislamiento entre usuarios con pruebas unitarias, de integración y de contratos pertinentes.
- [ ] Verificar el caso correcto y el error relevante; agregar pruebas backend cuando corresponda.
- [ ] Actualizar la documentación afectada.

## Dependencias
- RES-12
- RES-13

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.


## RES-15 — Documentar arquitectura, contratos y decisiones

Hito: **Robustez y entrega** · Etiquetas: `documentacion`, `prioridad:media`

<!-- backlog:RES-15 -->
## Objetivo
Documentar arquitectura, contratos y decisiones.

## Criterios de aceptación
- [ ] Incluir diagrama general, modelo propio, estados, seguridad, idempotencia, recuperación y ejecución de pruebas; documentar transporte seguro, CORS y validación de entradas.

## Dependencias
- RES-14

## Cierre
Cumplir los criterios, verificar el resultado e integrar el PR en `dev`.
