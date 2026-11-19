package reservas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reservas.integracion.infrastructure.kafka.KafkaCatedraAdapter;
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient.Hold;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient.Confirmacion;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.domain.ports.out.PublicarTelefonoPort.EnvioTelefono;
import reservas.procesoreserva.domain.ports.out.PublicarTelefonoPort.Resultado;
import reservas.procesoreserva.infrastructure.kafka.AccionesReservaListener;
import reservas.usuario.domain.ports.out.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProcesoHoldIntegrationTest {
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static final String PATH = "/api/appointment-holds";
    private static final LocalDate FECHA = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(2);
    private static final LocalTime HORA = LocalTime.of(9, 30);
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @MockitoBean private HoldsCatedraClient catedra;
    @MockitoBean private KafkaCatedraAdapter kafka;
    @Autowired private ProcesoReservaRepository procesos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccionesReservaListener acciones;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Test
    void guardaPedidoKafkaAntesDeLaRespuestaHttpYPublicaSoloUnaVez() throws Exception {
        var dueño = usuario();
        var otro = usuario();
        var hold = prepararHold(dueño, Instant.now().plusSeconds(240));
        long id = json.readTree(post(PATH, dueño.token(), slot()).body()).get("id").asLong();
        String phonePath = PATH + "/" + id + "/phone";
        String pedido = UUID.randomUUID().toString();
        when(catedra.confirmar(hold.holdId(), dueño.externalId(), "Ana", "Pérez")).thenAnswer(inv -> {
            recibir(hold, pedido, "AdditionalInformationRequested", 1);
            recibir(hold, pedido, "AdditionalInformationRequested", 2);
            return new Confirmacion(hold.reservationProcessId(), hold.expiresAt());
        });

        assertThat(post(confirmar(id), dueño.token(), null).statusCode()).isEqualTo(202);
        assertThat(obtener(phonePath, dueño.token()).body()).contains("PHONE_REQUIRED");
        assertThat(obtener(phonePath, otro.token()).statusCode()).isEqualTo(404);
        assertThat(post(phonePath, null, Map.of("phoneNumber", "1234567")).statusCode()).isEqualTo(401);
        assertThat(post(phonePath, dueño.token(), Map.of("phoneNumber", "abc")).statusCode()).isEqualTo(400);
        when(kafka.publicar(any(EnvioTelefono.class))).thenAnswer(inv -> {
            EnvioTelefono envio = inv.getArgument(0);
            assertThat(envio.requestEventId()).isEqualTo(pedido);
            assertThat(envio.reservationProcessId()).isEqualTo(hold.reservationProcessId());
            assertThat(envio.holdId()).isEqualTo(hold.holdId());
            assertThat(envio.phoneNumber()).isEqualTo("+123456789");
            assertThat(jdbc.queryForObject("SELECT submission_state FROM reservation_action WHERE event_id = ?",
                    String.class, pedido)).isEqualTo("OUTCOME_UNKNOWN");
            return Resultado.PUBLICADO;
        });

        for (int i = 0; i < 2; i++) {
            var respuesta = post(phonePath, dueño.token(), Map.of("phoneNumber", "+1 (234)-567.89"));
            assertThat(respuesta.statusCode()).isEqualTo(202);
            assertThat(respuesta.body()).contains("PUBLISHED").doesNotContain("+123456789", pedido);
        }
        verify(kafka, times(1)).publicar(any(EnvioTelefono.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_action WHERE event_id = ?", Long.class, pedido))
                .isEqualTo(1L);
        String resultado = UUID.randomUUID().toString();
        recibir(hold, resultado, "AppointmentConfirmed", 3);
        assertThat(jdbc.queryForObject("SELECT details->>'reservationId' FROM reservation_action WHERE event_id = ?",
                String.class, resultado)).isEqualTo("123");
        assertThat(estadoLocal(dueño)).isEqualTo("WAITING_FOR_PHONE");
    }

    @Test
    void unEnvioInciertoNoSeRepiteYElPlazoSeCierra() throws Exception {
        var dueño = usuario();
        var hold = prepararHold(dueño, Instant.now().plusSeconds(240));
        long id = json.readTree(post(PATH, dueño.token(), slot()).body()).get("id").asLong();
        when(catedra.confirmar(hold.holdId(), dueño.externalId(), "Ana", "Pérez"))
                .thenReturn(new Confirmacion(hold.reservationProcessId(), hold.expiresAt()));
        assertThat(post(confirmar(id), dueño.token(), null).statusCode()).isEqualTo(202);
        String phonePath = PATH + "/" + id + "/phone";
        assertThat(obtener(phonePath, dueño.token()).body()).contains("WAITING_FOR_REQUEST");
        String pedido = UUID.randomUUID().toString();
        recibir(hold, pedido, "AdditionalInformationRequested", 1);
        when(kafka.publicar(any(EnvioTelefono.class))).thenReturn(Resultado.NO_INICIADO, Resultado.INDETERMINADO);
        var telefono = Map.of("phoneNumber", "1234567");
        assertThat(post(phonePath, dueño.token(), telefono).body()).contains("PHONE_INTEGRATION_UNAVAILABLE");
        for (int i = 0; i < 2; i++) {
            var respuesta = post(phonePath, dueño.token(), telefono);
            assertThat(respuesta.statusCode()).isEqualTo(503);
            assertThat(respuesta.body()).contains("OUTCOME_UNKNOWN");
        }
        jdbc.update("UPDATE reservation_action SET expires_at = now() - interval '1 second' WHERE event_id = ?", pedido);
        assertThat(obtener(phonePath, dueño.token()).body()).contains("PHONE_WINDOW_CLOSED");
        assertThat(post(phonePath, dueño.token(), telefono).statusCode()).isEqualTo(410);
        assertThat(estadoLocal(dueño)).isEqualTo("WAITING_FOR_PHONE");
        verify(kafka, times(2)).publicar(any(EnvioTelefono.class));
    }

    private void recibir(Hold hold, String id, String tipo, long offset) {
        var evento = Map.of("eventType", tipo, "eventId", id, "schemaVersion", 1,
                "reservationProcessId", hold.reservationProcessId(), "holdId", hold.holdId(),
                "occurredAt", Instant.now().toString(), "expiresAt", hold.expiresAt().toString(),
                "requiredField", "PHONE_NUMBER", "reservationId", 123, "unknownField", "ignorar");
        var ack = mock(Acknowledgment.class);
        acciones.onMessage(new ConsumerRecord<>("acciones", 0, offset, hold.reservationProcessId(),
                json.writeValueAsString(evento)), ack);
        verify(ack).acknowledge();
    }

    @Test
    void guardaDueñoAntesDelPostYSoloConfirmaUnaVezConDosSolicitudes() throws Exception {
        var dueño = usuario();
        var otro = usuario();
        var hold = prepararHold(dueño, Instant.now().plusSeconds(240));
        var creado = post(PATH, dueño.token(), slot());
        assertThat(creado.statusCode()).isEqualTo(201);
        long id = json.readTree(creado.body()).get("id").asLong();
        assertThat(json.readTree(creado.body()).get("status").asString()).isEqualTo("HELD");
        assertThat(creado.body()).doesNotContain(hold.holdId(), hold.reservationProcessId());
        assertThat(post(confirmar(id), otro.token(), null).statusCode()).isEqualTo(404);
        assertThat(post(confirmar(id), null, null).statusCode()).isEqualTo(401);
        assertThat(procesos.findByIdAndUserId(id, dueño.id()).orElseThrow().getReservationProcessId())
                .isEqualTo(hold.reservationProcessId());

        var entro = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        when(catedra.confirmar(hold.holdId(), dueño.externalId(), "Ana", "Pérez")).thenAnswer(inv -> {
            try {
                assertThat(procesos.findByIdAndUserId(id, dueño.id()).orElseThrow().getStatus()).isEqualTo("CONFIRMING");
                entro.countDown();
                assertThat(liberar.await(8, TimeUnit.SECONDS)).isTrue();
                return new Confirmacion(hold.reservationProcessId(), hold.expiresAt());
            } finally {
                entro.countDown();
            }
        });
        var primera = http.sendAsync(request(confirmar(id), dueño.token(), null), HttpResponse.BodyHandlers.ofString());
        try {
            assertThat(entro.await(8, TimeUnit.SECONDS)).isTrue();
            var segunda = post(confirmar(id), dueño.token(), null);
            assertThat(segunda.statusCode()).isEqualTo(409);
            assertThat(segunda.body()).contains("CONFIRMATION_IN_PROGRESS");
        } finally {
            liberar.countDown();
        }
        var confirmada = primera.get(8, TimeUnit.SECONDS);
        assertThat(confirmada.statusCode()).isEqualTo(202);
        assertThat(confirmada.body()).contains("WAITING_FOR_PHONE");
        assertThat(post(confirmar(id), dueño.token(), null).statusCode()).isEqualTo(202);
        verify(catedra, times(1)).confirmar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void conservaElResultadoIndeterminadoYBloqueaOtroPostDeConfirmacion() throws Exception {
        var dueño = usuario();
        var hold = prepararHold(dueño, Instant.now().plusSeconds(240));
        long id = json.readTree(post(PATH, dueño.token(), slot()).body()).get("id").asLong();
        when(catedra.confirmar(hold.holdId(), dueño.externalId(), "Ana", "Pérez"))
                .thenThrow(new HoldCatedraException(HoldCatedraException.Error.RESULTADO_INDETERMINADO));
        for (int i = 0; i < 2; i++) {
            var respuesta = post(confirmar(id), dueño.token(), null);
            assertThat(respuesta.statusCode()).isEqualTo(503);
            assertThat(respuesta.body()).contains("HOLD_OUTCOME_UNKNOWN");
        }
        assertThat(procesos.findByIdAndUserId(id, dueño.id()).orElseThrow().getStatus()).isEqualTo("CONFIRM_UNKNOWN");
        verify(catedra, times(1)).confirmar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void elVencimientoAutoritativoImpideConfirmarYElVersionadoRechazaUnaEscrituraAtrasada() throws Exception {
        var dueño = usuario();
        prepararHold(dueño, Instant.now().minusSeconds(1));
        long id = json.readTree(post(PATH, dueño.token(), slot()).body()).get("id").asLong();
        assertThat(post(confirmar(id), dueño.token(), null).statusCode()).isEqualTo(410);
        verify(catedra, never()).confirmar(anyString(), anyString(), anyString(), anyString());

        var primero = procesos.findByIdAndUserId(id, dueño.id()).orElseThrow();
        var atrasado = procesos.findByIdAndUserId(id, dueño.id()).orElseThrow();
        primero.setStatus("EXPIRED");
        procesos.save(primero);
        atrasado.setStatus("CONFIRMING");
        assertThatThrownBy(() -> procesos.save(atrasado)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(procesos.findByIdAndUserId(id, dueño.id()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void devuelveConflictoCuandoCatedraInformaQueElSlotEstaOcupado() throws Exception {
        var dueño = usuario();
        when(catedra.crear(207L, FECHA, HORA, dueño.externalId()))
                .thenThrow(new HoldCatedraException(HoldCatedraException.Error.SLOT_OCUPADO));
        var respuesta = post(PATH, dueño.token(), slot());
        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(respuesta.body()).contains("SLOT_UNAVAILABLE");
    }

    @Test
    void guardaLaIntencionAntesDelPostYConservaLaCreacionIndeterminada() throws Exception {
        var dueño = usuario();
        when(catedra.crear(207L, FECHA, HORA, dueño.externalId())).thenAnswer(inv -> {
            assertThat(estadoLocal(dueño)).isEqualTo("CREATING");
            throw new HoldCatedraException(HoldCatedraException.Error.RESULTADO_INDETERMINADO);
        });

        var respuesta = post(PATH, dueño.token(), slot());
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.body()).contains("HOLD_OUTCOME_UNKNOWN");
        assertThat(estadoLocal(dueño)).isEqualTo("CREATE_UNKNOWN");
        verify(catedra, times(1)).crear(207L, FECHA, HORA, dueño.externalId());
    }

    private String estadoLocal(Cuenta cuenta) {
        return jdbc.queryForObject("SELECT status FROM reservation_process WHERE user_id = ?", String.class, cuenta.id());
    }

    private Hold prepararHold(Cuenta cuenta, Instant expiresAt) {
        var hold = new Hold("hold-" + UUID.randomUUID(), "process-" + UUID.randomUUID(), expiresAt);
        when(catedra.crear(207L, FECHA, HORA, cuenta.externalId())).thenReturn(hold);
        return hold;
    }

    private Cuenta usuario() throws Exception {
        String login = "hold-" + UUID.randomUUID();
        String clave = "clave-ficticia";
        var registro = post("/api/register", null, Map.of("login", login, "password", clave, "firstName", "Ana",
                "lastName", "Pérez", "email", login + "@example.test", "langKey", "es"));
        assertThat(registro.statusCode()).isEqualTo(201);
        var respuesta = post("/api/authenticate", null, Map.of("username", login, "password", clave));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        long id = usuarios.findByLoginIgnoreCase(login).orElseThrow().getId();
        return new Cuenta(id, json.readTree(respuesta.body()).get("id_token").asString());
    }

    private Map<String, Object> slot() {
        return Map.of("professionalId", 207, "date", FECHA.toString(), "startTime", HORA.toString());
    }

    private String confirmar(long id) { return PATH + "/" + id + "/confirm"; }

    private HttpResponse<String> post(String path, String token, Object payload) throws Exception {
        return http.send(request(path, token, payload), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> obtener(String path, String token) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path, String token, Object payload) {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (payload == null) return builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        return builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
    }

    private record Cuenta(long id, String token) {
        String externalId() { return "user-" + id; }
    }
}
