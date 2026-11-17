package reservas.disponibilidad;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException.Error;
import reservas.disponibilidad.infrastructure.client.adapter.CatedraOcupacionesHttpAdapter;
import reservas.disponibilidad.infrastructure.client.mapper.OcupacionesMapper;
import reservas.shared.infrastructure.catedra.CatedraProperties;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class OcupacionesHttpTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 23);
    private static final String TOKEN = "tecnico-ficticio-solo-pruebas";
    private static final String URL = "https://catedra.test/api/appointment-occupancies?professionalId=42&from=2026-11-23&to=2026-11-23";
    private static final String WRAPPER = "{\"professionalId\":42,\"from\":\"2026-11-23\",\"to\":\"2026-11-23\",\"occupancies\":%s,\"campoNuevo\":true}";
    @TempDir Path temp;
    private CatedraProperties properties;
    private CatedraOcupacionesHttpAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void preparar() throws Exception {
        properties = new CatedraProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("https://catedra.test"));
        properties.setTokenFile(temp.resolve("tecnico.jwt"));
        Files.writeString(properties.getTokenFile(), TOKEN);
        var builder = RestClient.builder().baseUrl("https://catedra.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new CatedraOcupacionesHttpAdapter(builder.build(), new CatedraTokenFile(properties), new OcupacionesMapper());
    }

    @Test
    void consultaUnSoloDiaConJwtTecnicoYExcluyeDuplicadosSinExpirarHoldsLocalmente(CapturedOutput output) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(WRAPPER.formatted("""
                        [{"date":"2026-11-23","startTime":"09:00","status":"CONFIRMED","expiresAt":null},
                         {"date":"2026-11-23","startTime":"09:00","status":"HELD","expiresAt":"2020-01-01T00:00:00Z"},
                         {"date":"2026-11-23","startTime":"09:30","status":"HELD","expiresAt":"2026-11-23T13:00:00Z"}]
                        """), MediaType.APPLICATION_JSON));
        assertThat(adapter.consultar(42L, DATE)).containsExactlyInAnyOrder(LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    @Test
    void listaVaciaValidaYReleeElTokenExterno() throws Exception {
        Files.writeString(properties.getTokenFile(), "token-renovado-ficticio");
        server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer token-renovado-ficticio"))
                .andRespond(withSuccess(WRAPPER.formatted("[]"), MediaType.APPLICATION_JSON));
        assertThat(adapter.consultar(42L, DATE)).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("erroresHttp")
    void traduceErroresSinCuerpoNiCausaYNoReintenta(int status, Error error, CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"" + TOKEN + "\"}"));
        assertError(error);
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    static Stream<Arguments> erroresHttp() {
        return Stream.of(Arguments.of(401, Error.NO_AUTENTICADO), Arguments.of(403, Error.ACCESO_DENEGADO),
                Arguments.of(429, Error.NO_DISPONIBLE), Arguments.of(503, Error.NO_DISPONIBLE),
                Arguments.of(400, Error.ERROR_CONTRATO), Arguments.of(404, Error.ERROR_CONTRATO),
                Arguments.of(302, Error.ERROR_CONTRATO));
    }

    @ParameterizedTest
    @MethodSource("respuestasInvalidas")
    void rechazaRespuestaIncompletaOMetadataIncorrecta(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    static Stream<String> respuestasInvalidas() {
        String row = "{\"date\":\"2026-11-23\",\"startTime\":\"09:00\",\"status\":\"CONFIRMED\"}";
        return Stream.of("", "null", "{", "{}", WRAPPER.formatted("null"), WRAPPER.formatted("[null]"),
                WRAPPER.formatted("[]").replace("\"professionalId\":42", "\"professionalId\":43"),
                WRAPPER.formatted("[]").replace("\"from\":\"2026-11-23\"", "\"from\":\"2026-11-22\""),
                WRAPPER.formatted("[]").replace("\"to\":\"2026-11-23\"", "\"to\":\"2026-11-24\""),
                WRAPPER.formatted("[" + row.replace("CONFIRMED", "UNKNOWN") + "]"),
                WRAPPER.formatted("[" + row.replace("2026-11-23", "2026-11-22") + "]"),
                WRAPPER.formatted("[" + row.replace("09:00", "25:00") + "]"),
                WRAPPER.formatted("[" + row.replace("CONFIRMED", "HELD") + "]"));
    }

    @Test
    void falloDeTransporteSeTraduceSinFiltrarLaExcepcion() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException(TOKEN)));
        assertError(Error.NO_DISPONIBLE);
        server.verify();
    }

    @Test
    void sinArchivoNoHaceHttp() throws Exception {
        Files.delete(properties.getTokenFile());
        assertError(Error.CONFIGURACION_INVALIDA);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer token", "token\r\ninyectado"})
    void rechazaCredencialInvalidaAntesDeHttp(String token) throws Exception {
        Files.writeString(properties.getTokenFile(), token);
        assertError(Error.CONFIGURACION_INVALIDA);
        server.verify();
    }

    private void assertError(Error error) {
        assertThatThrownBy(() -> adapter.consultar(42L, DATE))
                .isInstanceOfSatisfying(ConsultaOcupacionesException.class, exception -> {
                    assertThat(exception.getError()).isEqualTo(error);
                    assertThat(exception.getMessage()).doesNotContain(TOKEN);
                    assertThat(exception.getCause()).isNull();
                });
    }
}
