package reservas;

import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reservas.usuario.application.exception.EmailYaRegistradoException;
import reservas.usuario.application.exception.LoginYaRegistradoException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private ProcesoReservaRepository processes;

    @Test
    void savesUsersWithAuthoritiesAndFindsLoginIgnoringCase() {
        Usuario user = users.save(user());

        Usuario loaded = users.findByLoginIgnoreCase(user.getLogin().toUpperCase()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(user.getId());
        assertThat(loaded.getAuthorities()).containsExactly("ROLE_USER");
        assertThat(loaded.getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    @Test
    void rejectsDuplicateLoginAndEmailIgnoringCase() {
        Usuario original = users.save(user());

        Usuario duplicateLogin = user();
        duplicateLogin.setLogin(original.getLogin().toUpperCase());

        assertThatThrownBy(() -> users.save(duplicateLogin))
                .isInstanceOf(LoginYaRegistradoException.class);

        Usuario duplicateEmail = user();
        duplicateEmail.setEmail(original.getEmail().toUpperCase());

        assertThatThrownBy(() -> users.save(duplicateEmail))
                .isInstanceOf(EmailYaRegistradoException.class);

        Usuario loaded = users.findById(original.getId()).orElseThrow();
        assertThat(loaded.getLogin()).isEqualTo(original.getLogin());
        assertThat(loaded.getEmail()).isEqualTo(original.getEmail());
    }

    @Test
    void doesNotTranslateOtherIntegrityErrorsAsDuplicateUsers() {
        Usuario invalid = user();
        invalid.setLogin("");

        assertThatThrownBy(() -> users.save(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsOwnershipWhenEnrichingTheSameProcessWithReservationData() {
        Usuario owner = users.save(user());
        Usuario other = users.save(user());
        ProcesoReserva initial = processes.save(process(owner.getId()));
        assertThat(initial.getId()).isNotNull();
        assertThat(initial.getReservationProcessId()).isNull();
        assertThat(processes.findByIdAndUserId(initial.getId(), other.getId())).isEmpty();

        initial.setHoldId("hold-" + UUID.randomUUID());
        initial.setReservationProcessId("process-" + UUID.randomUUID());
        initial.setReservationId(8251L);
        initial.setStatus("CONFIRMED");
        initial.setConfirmedAt(Instant.now());
        ProcesoReserva confirmed = processes.save(initial);

        assertThat(confirmed.getId()).isEqualTo(initial.getId());
        assertThat(confirmed.getUserId()).isEqualTo(owner.getId());
        assertThat(processes.findByReservationProcessIdAndUserId(
                confirmed.getReservationProcessId(), owner.getId())).isPresent();
        assertThat(processes.findByReservationProcessIdAndUserId(
                confirmed.getReservationProcessId(), other.getId())).isEmpty();
    }

    @Test
    void rejectsMissingOwnerAndDuplicateExternalProcessId() {
        assertThatThrownBy(() -> processes.save(process(Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Usuario owner = users.save(user());
        ProcesoReserva first = process(owner.getId());
        first.setReservationProcessId("process-" + UUID.randomUUID());
        processes.save(first);

        ProcesoReserva duplicate = process(owner.getId());
        duplicate.setReservationProcessId(first.getReservationProcessId());
        assertThatThrownBy(() -> processes.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAStaleConcurrentUpdate() {
        Usuario owner = users.save(user());
        ProcesoReserva saved = processes.save(process(owner.getId()));
        ProcesoReserva first = processes.findByIdAndUserId(saved.getId(), owner.getId()).orElseThrow();
        ProcesoReserva stale = processes.findByIdAndUserId(saved.getId(), owner.getId()).orElseThrow();

        first.setStatus("HELD");
        processes.save(first);
        stale.setStatus("WAITING_FOR_PHONE");

        assertThatThrownBy(() -> processes.save(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(processes.findByIdAndUserId(saved.getId(), owner.getId()).orElseThrow().getStatus())
                .isEqualTo("HELD");
    }

    private Usuario user() {
        String login = "user-" + UUID.randomUUID();
        Instant now = Instant.now();
        return Usuario.builder().login(login).email(login + "@example.com")
                .passwordHash("hash-ficticio-solo-para-prueba-de-persistencia")
                .firstName("Juan").lastName("Perez").langKey("es").activated(true)
                .authorities(Set.of("ROLE_USER")).createdAt(now).updatedAt(now).build();
    }

    private ProcesoReserva process(Long userId) {
        Instant now = Instant.now();
        return ProcesoReserva.builder().userId(userId).professionalId(15L)
                .appointmentDate(LocalDate.of(2026, 11, 2)).startTime(LocalTime.of(9, 30))
                .status("CREATED").createdAt(now).updatedAt(now).build();
    }
}
