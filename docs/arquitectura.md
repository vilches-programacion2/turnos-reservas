# Arquitectura de reservas

Base: Java 25, Spring Boot 4.1.0, Lombok y PostgreSQL 17. Lombok `@Slf4j`
genera el logger; SLF4J y Logback son quienes emiten los logs. No registrar
contraseñas, JWT, teléfonos, cuerpos completos ni configuración sensible.

Se sigue la skill `hexagonal-slicing`, con PostgreSQL en lugar de H2 porque
la consigna exige una base servidor. El bootstrap solo contiene configuración
transversal en `shared.infrastructure`; las funcionalidades se incorporarán
cuando se implementen sus casos de uso, sin publicar CRUD genéricos de usuarios.

Cada funcionalidad seguirá esta estructura bajo `ar.edu.um.turnos.reservas`:

```text
usuario/                         # luego: reserva/
  domain/model/                  # modelos sin JPA, Jackson ni Spring
  domain/ports/in/               # una interfaz por caso de uso
  domain/ports/out/              # repositorios y servicios externos
  application/usecases/          # implementación de los puertos de entrada
  application/service/           # fachada para adaptadores de entrada
  application/exception/
  infrastructure/persistence/    # entity, repository, mapper, adapter
  infrastructure/web/            # controller, dto, mapper
  infrastructure/client/         # adaptadores HTTP salientes
  infrastructure/messaging/      # adaptadores Kafka
```

Los controladores delegan a la fachada; los casos de uso dependen de puertos,
nunca de repositorios JPA o clientes HTTP concretos. Dominio y aplicación no
importan infraestructura. Entidades JPA y DTO no cruzan esas fronteras.
Los mappers son componentes explícitos y toleran entradas nulas.

RES-02 incorpora Flyway y persistencia de usuarios/procesos propios; ver
[modelo y límites de persistencia](persistencia.md).
Hibernate está configurado con `validate`: no crea ni modifica tablas.
El servicio de catálogo tendrá su propia base y credenciales, sin permisos
cruzados. El Compose actual es únicamente el arranque local de reservas;
RES-13 lo ampliará a ambos backends. Cátedra se consume externamente.

RES-03 y RES-04 implementarán registro y JWT. Hasta entonces, solamente los
GET de salud están habilitados; no existe autenticación con usuarios de prueba
ni acceso público a operaciones de negocio. Readiness depende de PostgreSQL;
liveness solo indica si el proceso vive. Ambos ocultan detalles internos.

Los contratos externos y las propuestas internas están en
[contratos-base.md](contratos-base.md).
