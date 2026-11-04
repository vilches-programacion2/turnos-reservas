package reservas;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.application.exception.ConsultaCatalogoException.Error;
import reservas.catalogo.application.service.CatalogoService;
import reservas.catalogo.application.usecases.ConsultarAgendaProfesionalUseCaseImpl;
import reservas.catalogo.application.usecases.ConsultarProfesionalUseCaseImpl;
import reservas.catalogo.infrastructure.client.adapter.CatalogoHttpAdapter;
import reservas.catalogo.infrastructure.client.mapper.CatalogoMapper;
import reservas.catalogo.infrastructure.config.CatalogoProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class CatalogoClientTest {
    static final String BASE = "https://catalogo.test";
    static final String TOKEN = "jwt-usuario-simulado-no-tecnico";
    static final String PROFESIONAL = """
            {"id":42,"categoryId":3,"firstName":"Ana","lastName":"Gómez","enabled":true}
            """;
    static final String AGENDA = """
            {"versionAplicada":7,"professional":%s,"weeklySchedules":[
              {"id":9,"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"12:00:00",
               "slotDurationMinutes":30}],"campoCompatibleNuevo":true}
            """.formatted(PROFESIONAL);

    private MockRestServiceServer server;
    private CatalogoService service;

    @BeforeEach
    void preparar() {
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new CatalogoProperties(URI.create(BASE), "turnos-catalogo",
                Duration.ofSeconds(3), Duration.ofSeconds(5));
        var adapter = new CatalogoHttpAdapter(builder.build(), properties, new CatalogoMapper());
        service = new CatalogoService(new ConsultarProfesionalUseCaseImpl(adapter), new ConsultarAgendaProfesionalUseCaseImpl(adapter));
        autenticar(List.of("turnos-reservas", "turnos-catalogo"), "ROLE_USER");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultaProfesionalYAgendaConElMismoJwtDelUsuario() {
        esperar("").andRespond(withSuccess(PROFESIONAL, MediaType.APPLICATION_JSON));
        esperar("/schedule").andRespond(withSuccess(AGENDA, MediaType.APPLICATION_JSON));
        var profesional = service.consultarProfesional(42L);
        assertThat(profesional.getId()).isEqualTo(42L);
        assertThat(profesional.getCategoryId()).isEqualTo(3L);
        assertThat(profesional.getFirstName()).isEqualTo("Ana");
        assertThat(profesional.getLastName()).isEqualTo("Gómez");
        assertThat(profesional.getEnabled()).isTrue();
        var agenda = service.consultarAgenda(42L);
        assertThat(agenda.getVersionAplicada()).isEqualTo(7L);
        assertThat(agenda.getProfessional().getId()).isEqualTo(42L);
        assertThat(agenda.getWeeklySchedules()).singleElement().satisfies(horario -> {
            assertThat(horario.getId()).isEqualTo(9L);
            assertThat(horario.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
            assertThat(horario.getStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(horario.getEndTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(horario.getSlotDurationMinutes()).isEqualTo(30);
        });
        server.verify();
    }

    @Test
    void detalleDeshabilitadoNoSeConfundeConProfesionalInexistente() {
        esperar("").andRespond(withSuccess(PROFESIONAL.replace("true", "false"), MediaType.APPLICATION_JSON));
        assertThat(service.consultarProfesional(42L).getEnabled()).isFalse();
        server.verify();
    }

    @Test
    void versionCeroYAgendaSinHorariosSonRespuestasLegitimas() {
        esperar("/schedule").andRespond(withSuccess("""
                {"versionAplicada":0,"professional":%s,"weeklySchedules":[]}
                """.formatted(PROFESIONAL), MediaType.APPLICATION_JSON));
        var agenda = service.consultarAgenda(42L);
        assertThat(agenda.getVersionAplicada()).isZero();
        assertThat(agenda.getWeeklySchedules()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("erroresHttp")
    void traduceEstadoYCodigoSinFiltrarDatosNiDevolverUnFallback(int status, String code, Error error, CapturedOutput output) {
        esperar("/schedule").andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"code\":\"" + code + "\",\"detail\":\"" + TOKEN + "\"}"));
        assertThatThrownBy(() -> service.consultarAgenda(42L)).isInstanceOfSatisfying(ConsultaCatalogoException.class, e -> {
            assertThat(e.getError()).isEqualTo(error);
            assertThat(e.getHttpStatus()).isEqualTo(status);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN);
        });
        assertThat(output).contains("operacion=agenda", "profesionalId=42", "usuarioId=17")
                .doesNotContain(TOKEN);
        server.verify();
    }

    static Stream<Arguments> erroresHttp() {
        return Stream.of(Arguments.of(404, "PROFESSIONAL_NOT_FOUND", Error.PROFESIONAL_NO_ENCONTRADO),
                Arguments.of(409, "PROFESSIONAL_DISABLED", Error.PROFESIONAL_DESHABILITADO),
                Arguments.of(503, "CATALOG_NOT_READY", Error.NO_INICIALIZADO),
                Arguments.of(503, "CATALOG_UNAVAILABLE", Error.NO_DISPONIBLE),
                Arguments.of(503, "DESCONOCIDO", Error.NO_DISPONIBLE),
                Arguments.of(401, "DESCONOCIDO", Error.AUTENTICACION_REQUERIDA),
                Arguments.of(403, "DESCONOCIDO", Error.ACCESO_DENEGADO),
                Arguments.of(404, "DESCONOCIDO", Error.ERROR_CONTRATO),
                Arguments.of(400, "INVALID_REQUEST", Error.ERROR_CONTRATO));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void toleraRechazosDeSeguridadSinCuerpo(int status) {
        esperar("").andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertError(() -> service.consultarProfesional(42L), status == 401 ? Error.AUTENTICACION_REQUERIDA : Error.ACCESO_DENEGADO);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "{\"versionAplicada\":7}"})
    void rechazaAgendaAusenteIncompletaOMalformada(String body) {
        esperar("/schedule").andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(() -> service.consultarAgenda(42L), Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"identidad", "version", "dia", "duracion", "deshabilitado"})
    void rechazaDatosInconsistentesDelContrato(String caso) {
        String body = switch (caso) {
            case "identidad" -> AGENDA.replace("\"id\":42", "\"id\":43");
            case "version" -> AGENDA.replace("\"versionAplicada\":7", "\"versionAplicada\":-1");
            case "dia" -> AGENDA.replace("MONDAY", "INVALIDO");
            case "duracion" -> AGENDA.replace("Minutes\":30", "Minutes\":0");
            default -> AGENDA.replace("true", "false");
        };
        esperar("/schedule").andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(() -> service.consultarAgenda(42L), Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    @Test
    void desconexionNoReintentaNiConservaMensajesQuePuedenContenerTokens(CapturedOutput output) {
        esperar("").andRespond(withException(new IOException("Error externo " + TOKEN)));
        assertThatThrownBy(() -> service.consultarProfesional(42L))
                .isInstanceOfSatisfying(ConsultaCatalogoException.class, e -> {
                    assertThat(e.getError()).isEqualTo(Error.NO_DISPONIBLE);
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                    assertThat(e.getCause()).isNull();
                });
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonimo", "otra-autenticacion", "sin-audiencia", "rol-tecnico", "no-autenticado"})
    void rechazaIdentidadesNoAutorizadasAntesDeContactarCatalogo(String caso) {
        Error error = Error.AUTENTICACION_REQUERIDA;
        switch (caso) {
            case "anonimo" -> SecurityContextHolder.clearContext();
            case "otra-autenticacion" -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("usuario", "no-es-jwt", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            case "sin-audiencia" -> autenticar(List.of("turnos-reservas"), "ROLE_USER");
            case "no-autenticado" -> SecurityContextHolder.getContext().getAuthentication().setAuthenticated(false);
            default -> {
                autenticar(List.of("turnos-catalogo"), "ROLE_STUDENT_CLIENT");
                error = Error.ACCESO_DENEGADO;
            }
        }
        assertError(() -> service.consultarProfesional(42L), error);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaIdsInvalidosAntesDeEnviar(Long id) {
        assertError(() -> service.consultarProfesional(id), Error.SOLICITUD_INVALIDA);
        server.verify();
    }

    static void autenticar(List<String> audiencias, String rol) {
        var jwt = Jwt.withTokenValue(TOKEN).header("alg", "RS256").subject("17")
                .issuer("https://reservas.test").audience(audiencias)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority(rol))));
    }

    private org.springframework.test.web.client.ResponseActions esperar(String suffix) {
        return server.expect(requestTo(BASE + "/api/professionals/42" + suffix))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer " + TOKEN));
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Error error) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ConsultaCatalogoException.class,
                e -> assertThat(e.getError()).isEqualTo(error));
    }
}
