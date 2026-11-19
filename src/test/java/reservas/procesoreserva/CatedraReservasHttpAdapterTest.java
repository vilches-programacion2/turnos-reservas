package reservas.procesoreserva;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.exception.ReservaPropiaException.Error;
import reservas.procesoreserva.infrastructure.client.CatedraReservasHttpAdapter;
import reservas.shared.infrastructure.catedra.CatedraProperties;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CatedraReservasHttpAdapterTest {
    private static final String BASE = "https://catedra.test";
    private static final String APPOINTMENTS = BASE + "/api/appointments";
    @TempDir Path temp;
    private MockRestServiceServer server;
    private CatedraReservasHttpAdapter adapter;

    @BeforeEach
    void preparar() throws IOException {
        var properties = new CatedraProperties();
        properties.setEnabled(true);
        properties.setTokenFile(Files.writeString(temp.resolve("test.jwt"), "token-ficticio"));
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new CatedraReservasHttpAdapter(builder.build(), new CatedraTokenFile(properties));
    }

    @Test
    void pideUnaPaginaFiltradaYLeeSoloLosCamposDeLaReserva() {
        server.expect(requestTo(APPOINTMENTS + "?externalPatientId=user-42&page=1&size=10"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer token-ficticio"))
                .andRespond(withSuccess("""
                        [{"reservationId":12,"reservationProcessId":"process-1","externalPatientId":"user-42",
                          "professionalId":207,"appointmentDate":"2026-11-25","startTime":"09:30:00",
                          "status":"CONFIRMED","patientPhone":"1234567"}]
                        """, MediaType.APPLICATION_JSON));
        var items = adapter.listar("user-42", 1, 10);
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.reservationId()).isEqualTo(12L);
            assertThat(item.toString()).doesNotContain("1234567");
        });
        server.verify();
    }

    @Test
    void cancelaConElIdExternoYValidaLaRespuesta() {
        server.expect(requestTo(APPOINTMENTS + "/process-1/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-ficticio")).andExpect(jsonPath("$.reason").value("Prueba"))
                .andRespond(withSuccess("""
                        {"reservationId":12,"reservationProcessId":"process-1","status":"CANCELLED",
                         "cancelledAt":"2026-11-25T12:40:00Z"}
                        """, MediaType.APPLICATION_JSON));
        assertThat(adapter.cancelar("process-1", "Prueba").cancelledAt()).isEqualTo(Instant.parse("2026-11-25T12:40:00Z"));
        server.verify();
    }

    @Test
    void falloAmbiguoConservaElResultadoDesconocidoYSoloEnviaUnPost() {
        server.expect(requestTo(APPOINTMENTS + "/process-1/cancel"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> adapter.cancelar("process-1", null))
                .isInstanceOfSatisfying(ReservaPropiaException.class,
                        e -> assertThat(e.getError()).isEqualTo(Error.CANCELACION_INDETERMINADA));
        server.verify();
    }
}
