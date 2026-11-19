package reservas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.application.service.IntegracionCatedraService;
import reservas.integracion.application.usecases.ObtenerIntegracionCatedraUseCaseImpl;
import reservas.integracion.application.usecases.PrepararIntegracionCatedraUseCaseImpl;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.infrastructure.client.adapter.CatedraHttpAdapter;
import reservas.integracion.infrastructure.client.mapper.IntegracionCatedraMapper;
import reservas.shared.infrastructure.catedra.CatedraConfiguration;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;
import reservas.shared.infrastructure.catedra.CatedraProperties;
import reservas.integracion.infrastructure.config.CatedraStartupCheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class CatedraIntegrationTest {
    private static final String BASE = "https://catedra.test";
    private static final String TOKEN = "token-tecnico-de-prueba";
    private static final String PASSWORD = "secreto-redis-de-prueba";
    static final String PROVISIONED = """
            {"groupId":"proyecto-prueba","redisHost":"redis.test","redisPort":6379,
             "redisUsername":"usuario-prueba","redisPassword":"secreto-redis-de-prueba",
             "redisReadNamespace":"catedra:sync:*","redisWriteNamespace":"alumnos:proyecto-prueba:*",
             "kafkaBootstrapServers":"kafka.test:9092","kafkaConsumerGroupId":"grupo-asignado",
             "kafkaCatalogTopic":"catalogo-asignado","kafkaAppointmentActionsTopic":"acciones-asignadas",
             "kafkaAppointmentPhoneTopic":"telefono-asignado","provisioningStatus":"PROVISIONED",
             "campoCompatibleNuevo":true}
            """;

    @TempDir Path temp;
    private CatedraProperties properties;
    private MockRestServiceServer server;
    private IntegracionCatedraService service;

    @BeforeEach
    void preparar() throws IOException {
        properties = new CatedraProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(java.net.URI.create(BASE));
        properties.setTokenFile(temp.resolve("catedra.jwt"));
        Files.writeString(properties.getTokenFile(), TOKEN + "\n");
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new CatedraHttpAdapter(builder.build(), new CatedraTokenFile(properties), new IntegracionCatedraMapper());
        var obtener = new ObtenerIntegracionCatedraUseCaseImpl(adapter);
        service = new IntegracionCatedraService(obtener, new PrepararIntegracionCatedraUseCaseImpl(obtener, integracion -> { }));
    }

    @Test
    void consultaConBearerYConservaLaConfiguracionAsignadaSinExponerla(CapturedOutput output) {
        esperarGet(TOKEN).andRespond(withSuccess(PROVISIONED, MediaType.APPLICATION_JSON));
        var integration = service.obtener();
        assertThat(integration.getProvisioningStatus()).isEqualTo(EstadoProvisionamiento.PROVISIONED);
        assertThat(integration.getGroupId()).isEqualTo("proyecto-prueba");
        assertThat(integration.getRedisHost()).isEqualTo("redis.test");
        assertThat(integration.getRedisPort()).isEqualTo(6379);
        assertThat(integration.getRedisUsername()).isEqualTo("usuario-prueba");
        assertThat(integration.getRedisPassword()).isEqualTo(PASSWORD);
        assertThat(integration.getRedisReadNamespace()).isEqualTo("catedra:sync:*");
        assertThat(integration.getRedisWriteNamespace()).isEqualTo("alumnos:proyecto-prueba:*");
        assertThat(integration.getKafkaBootstrapServers()).isEqualTo("kafka.test:9092");
        assertThat(integration.getKafkaConsumerGroupId()).isEqualTo("grupo-asignado");
        assertThat(integration.getKafkaCatalogTopic()).isEqualTo("catalogo-asignado");
        assertThat(integration.getKafkaAppointmentActionsTopic()).isEqualTo("acciones-asignadas");
        assertThat(integration.getKafkaAppointmentPhoneTopic()).isEqualTo("telefono-asignado");
        assertThat(integration.toString()).doesNotContain(PASSWORD);
        assertThat(output).doesNotContain(TOKEN, PASSWORD);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED", "REVOKED"})
    void admiteEstadosNoProvisionadosSinExigirPasswordRedis(String estado) {
        esperarGet(TOKEN).andRespond(withSuccess("""
                {"groupId":"proyecto-prueba","provisioningStatus":"%s"}
                """.formatted(estado), MediaType.APPLICATION_JSON));
        var integration = service.obtener();
        assertThat(integration.getProvisioningStatus()).isEqualTo(EstadoProvisionamiento.valueOf(estado));
        assertThat(integration.getRedisPassword()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("erroresHttp")
    void traduceRechazosSinReintentosNiFiltrarCuerpo(int status, Error error, CapturedOutput output) {
        esperarGet(TOKEN).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"detail\":\"" + TOKEN + " " + PASSWORD + "\"}"));
        assertThatThrownBy(service::obtener).isInstanceOfSatisfying(IntegracionCatedraException.class, e -> {
            assertThat(e.getError()).isEqualTo(error);
            assertThat(e.getHttpStatus()).isEqualTo(status);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN, PASSWORD);
        });
        assertThat(output).doesNotContain(TOKEN, PASSWORD);
        server.verify();
    }

    static Stream<Arguments> erroresHttp() {
        return Stream.of(Arguments.of(401, Error.NO_AUTENTICADO), Arguments.of(403, Error.ACCESO_DENEGADO),
                Arguments.of(404, Error.INTEGRACION_NO_ENCONTRADA), Arguments.of(503, Error.NO_DISPONIBLE),
                Arguments.of(429, Error.NO_DISPONIBLE), Arguments.of(400, Error.ERROR_HTTP),
                Arguments.of(302, Error.ERROR_HTTP));
    }

    @Test
    void toleraUn401SinCuerpo() {
        esperarGet(TOKEN).andRespond(withStatus(HttpStatusCode.valueOf(401)));
        assertError(Error.NO_AUTENTICADO);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "{\"id_token\":\"no-aceptar-wrapper\",\"integration\":{}}",
            "{\"groupId\":\"prueba\",\"provisioningStatus\":\"DESCONOCIDO\"}"})
    void rechazaRespuestasAusentesIncompletasOMalformadas(String body) {
        esperarGet(TOKEN).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertError(Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    @Test
    void provisionedExigeCredencialesYConfiguracionUtilizables() {
        esperarGet(TOKEN).andRespond(withSuccess(PROVISIONED.replace(PASSWORD, ""), MediaType.APPLICATION_JSON));
        assertError(Error.RESPUESTA_INVALIDA);
        server.verify();
    }

    @Test
    void desconexionNoFiltraLaExcepcionOriginal(CapturedOutput output) {
        esperarGet(TOKEN).andRespond(withException(new IOException("fallo con " + TOKEN)));
        assertThatThrownBy(service::obtener).isInstanceOfSatisfying(IntegracionCatedraException.class, e -> {
            assertThat(e.getError()).isEqualTo(Error.NO_DISPONIBLE);
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).doesNotContain(TOKEN);
        });
        assertThat(output).doesNotContain(TOKEN);
        server.verify();
    }

    @Test
    void releeElArchivoAlReemplazarElTokenSinHacerLogin() throws IOException {
        esperarGet(TOKEN).andRespond(withSuccess(PROVISIONED, MediaType.APPLICATION_JSON));
        esperarGet("otro-token-tecnico").andRespond(withSuccess(PROVISIONED, MediaType.APPLICATION_JSON));
        service.obtener();
        Files.writeString(properties.getTokenFile(), "otro-token-tecnico");
        service.obtener();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer token", "token\r\nX-Header: valor", "ausente", "demasiado-grande"})
    void archivoInvalidoImpideEnviarPeticiones(String caso) throws IOException {
        if (caso.equals("ausente")) {
            Files.delete(properties.getTokenFile());
        } else {
            Files.writeString(properties.getTokenFile(), caso.equals("demasiado-grande") ? "a".repeat(16385) : caso);
        }
        assertError(Error.CONFIGURACION_INVALIDA);
        server.verify();
    }

    @Test
    void deshabilitadaNoContactaCatedraNiLeeCredenciales() {
        properties.setEnabled(false);
        assertError(Error.CONFIGURACION_INVALIDA);
        server.verify();
    }

    @Test
    void compruebaDeNuevoTrasFallarElArranqueYNoReabreUnaIntegracionYaPreparada(CapturedOutput output) throws Exception {
        esperarGet(TOKEN).andRespond(withStatus(HttpStatusCode.valueOf(401)).body(TOKEN));
        esperarGet(TOKEN).andRespond(withSuccess(PROVISIONED, MediaType.APPLICATION_JSON));
        var startup = new CatedraStartupCheck(service);
        startup.run(new DefaultApplicationArguments());
        startup.prepararSiFalta();
        startup.prepararSiFalta();
        assertThat(output).contains("estado=PROVISIONED", "error=NO_AUTENTICADO", "httpStatus=401")
                .doesNotContain(TOKEN, PASSWORD, "redis.test", "usuario-prueba");
        server.verify();
    }

    @Test
    void configuracionDeshabilitadaArrancaSinUrlNiArchivo() {
        new ApplicationContextRunner().withUserConfiguration(CatedraConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RestClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://catedra.test", "https://usuario:password@catedra.test", "https://catedra.test?token=x"})
    void configuracionHabilitadaRechazaDestinosInvalidos(String url) {
        new ApplicationContextRunner().withUserConfiguration(CatedraConfiguration.class)
                .withPropertyValues("app.catedra.enabled=true", "app.catedra.base-url=" + url,
                        "app.catedra.token-file=" + properties.getTokenFile())
                .run(context -> assertThat(context).hasFailed());
    }

    private org.springframework.test.web.client.ResponseActions esperarGet(String token) {
        return server.expect(requestTo(BASE + "/api/student/integration"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer " + token));
    }

    private void assertError(Error error) {
        assertThatThrownBy(service::obtener).isInstanceOfSatisfying(IntegracionCatedraException.class,
                e -> assertThat(e.getError()).isEqualTo(error));
    }
}
