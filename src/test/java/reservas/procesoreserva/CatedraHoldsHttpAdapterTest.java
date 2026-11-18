package reservas.procesoreserva;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.application.exception.HoldCatedraException.Error;
import reservas.procesoreserva.infrastructure.client.CatedraHoldsHttpAdapter;
import reservas.shared.infrastructure.catedra.CatedraProperties;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CatedraHoldsHttpAdapterTest {
    private static final String BASE = "https://catedra.test";
    private static final String HOLDS = BASE + "/api/appointment-holds";
    private static final Instant VENCIMIENTO = Instant.parse("2026-11-25T12:40:00Z");
    @TempDir Path temp;
    private MockRestServiceServer server;
    private CatedraHoldsHttpAdapter adapter;

    @BeforeEach
    void preparar() throws IOException {
        var properties = new CatedraProperties();
        properties.setEnabled(true);
        properties.setTokenFile(Files.writeString(temp.resolve("test.jwt"), "token-ficticio"));
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new CatedraHoldsHttpAdapter(builder.build(), new CatedraTokenFile(properties));
    }

    @Test
    void usaElContratoYConservaLosIdentificadoresYElVencimiento() {
        server.expect(requestTo(HOLDS)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-ficticio"))
                .andExpect(jsonPath("$.externalPatientId").value("user-42"))
                .andExpect(jsonPath("$.professionalId").value(207))
                .andExpect(jsonPath("$.date").value("2026-11-25"))
                .andExpect(jsonPath("$.startTime").value(startsWith("09:30")))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"holdId":"hold-1","reservationProcessId":"process-1","status":"HELD",
                         "expiresAt":"2026-11-25T12:40:00Z","extra":true}
                        """));
        server.expect(requestTo(HOLDS + "/hold-1/confirm")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-ficticio"))
                .andExpect(jsonPath("$.externalPatientId").value("user-42"))
                .andExpect(jsonPath("$.patientFirstName").value("Ana"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"reservationProcessId":"process-1","status":"WAITING_FOR_PHONE",
                         "expiresAt":"2026-11-25T12:40:00Z"}
                        """));
        var hold = adapter.crear(207L, LocalDate.of(2026, 11, 25), LocalTime.of(9, 30), "user-42");
        var confirmacion = adapter.confirmar(hold.holdId(), "user-42", "Ana", "Pérez");
        assertThat(hold.reservationProcessId()).isEqualTo(confirmacion.reservationProcessId());
        assertThat(hold.expiresAt()).isEqualTo(VENCIMIENTO);
        assertThat(confirmacion.expiresAt()).isEqualTo(VENCIMIENTO);
        server.verify();
    }

    @Test
    void distingueUnRechazoDelSlotDeUnResultadoIndeterminado() {
        server.expect(requestTo(HOLDS)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"SLOT_ALREADY_HELD\"}"));
        assertThatThrownBy(() -> adapter.crear(207L, LocalDate.of(2026, 11, 25), LocalTime.of(9, 30), "user-42"))
                .isInstanceOfSatisfying(HoldCatedraException.class, e -> assertThat(e.getError()).isEqualTo(Error.SLOT_OCUPADO));
        server.verify();
    }

    @Test
    void unFalloDeTransporteNoReintentaElPost() {
        server.expect(requestTo(HOLDS + "/hold-1/confirm")).andRespond(withException(new IOException("timeout simulado")));
        assertThatThrownBy(() -> adapter.confirmar("hold-1", "user-42", "Ana", "Pérez"))
                .isInstanceOfSatisfying(HoldCatedraException.class, e -> {
                    assertThat(e.getError()).isEqualTo(Error.RESULTADO_INDETERMINADO);
                    assertThat(e.getCause()).isNull();
                });
        server.verify();
    }

    @Test
    void unaRespuestaExitosaSinLosIdentificadoresNoDescartaElPosibleHold() {
        server.expect(requestTo(HOLDS)).andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"status":"HELD","expiresAt":"2026-11-25T12:40:00Z"}
                        """));
        assertThatThrownBy(() -> adapter.crear(207L, LocalDate.of(2026, 11, 25), LocalTime.of(9, 30), "user-42"))
                .isInstanceOfSatisfying(HoldCatedraException.class,
                        e -> assertThat(e.getError()).isEqualTo(Error.RESULTADO_INDETERMINADO));
        server.verify();
    }
}
