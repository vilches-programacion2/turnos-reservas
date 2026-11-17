package reservas;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Import(DisponibilidadIntegrationTest.Reloj.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DisponibilidadIntegrationTest {
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");
    private static final String FECHA = "2026-11-23";
    private static final String PATH = "/api/professionals/42/availability?date=" + FECHA;
    private static final String TECNICO = "token-tecnico-ficticio-res07";
    private static final String OCUPADAS = """
            {"professionalId":42,"from":"2026-11-23","to":"2026-11-23","occupancies":[
              {"date":"2026-11-23","startTime":"09:30","status":"HELD","expiresAt":"2020-01-01T00:00:00Z"}]}
            """;
    private static final AtomicInteger CAT_REQUESTS = new AtomicInteger();
    private static final AtomicInteger CATEDRA_REQUESTS = new AtomicInteger();
    private static final AtomicInteger CAT_STATUS = new AtomicInteger(200);
    private static final AtomicInteger CATEDRA_STATUS = new AtomicInteger(200);
    private static final AtomicInteger DEMORA = new AtomicInteger();
    private static final AtomicReference<String> CAT_BODY = new AtomicReference<>(CatalogoClientTest.AGENDA);
    private static final AtomicReference<String> CATEDRA_BODY = new AtomicReference<>(OCUPADAS);
    private static final AtomicReference<String> CAT_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> CATEDRA_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> CATEDRA_QUERY = new AtomicReference<>();
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer CAT = servidor(false);
    private static final HttpServer CATEDRA = servidor(true);
    private static final Path TOKEN_FILE = tokenFile();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("app.catalogo.base-url", () -> base(CAT));
        registry.add("app.catedra.enabled", () -> "true");
        registry.add("app.catedra.base-url", () -> base(CATEDRA));
        registry.add("app.catedra.token-file", () -> TOKEN_FILE.toString());
        registry.add("app.catedra.read-timeout", () -> "250ms");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Reloj {
        @Bean @Primary Clock relojPrueba() {
            return Clock.fixed(Instant.parse("2026-11-22T20:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }

    @LocalServerPort private int port;
    @Autowired private JwtEncoder encoder;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void limpiar() {
        CAT_REQUESTS.set(0); CATEDRA_REQUESTS.set(0);
        CAT_STATUS.set(200); CATEDRA_STATUS.set(200); DEMORA.set(0);
        CAT_BODY.set(CatalogoClientTest.AGENDA); CATEDRA_BODY.set(OCUPADAS);
        CAT_AUTH.set(null); CATEDRA_AUTH.set(null); CATEDRA_QUERY.set(null);
    }

    @AfterAll
    static void cerrar() {
        CAT.stop(0); CATEDRA.stop(0); EXECUTOR.shutdownNow();
    }

    @Test
    void endpointCombinaClientesRealesYSeparaExactamenteAmbosJwt() throws Exception {
        String usuario = token("valido");
        var response = get(PATH, usuario);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        var body = json.readTree(response.body());
        assertThat(body.get("professionalId").asLong()).isEqualTo(42);
        assertThat(body.get("date").asString()).isEqualTo(FECHA);
        assertThat(body.get("versionAplicada").asLong()).isEqualTo(7);
        assertThat(body.get("availableSlots").size()).isEqualTo(5);
        assertThat(body.get("availableSlots").get(0).asString()).startsWith("09:00");
        assertThat(("Bearer " + usuario).equals(CAT_AUTH.get())).isTrue();
        assertThat(("Bearer " + TECNICO).equals(CATEDRA_AUTH.get())).isTrue();
        assertThat(CATEDRA_QUERY.get()).isEqualTo("professionalId=42&from=2026-11-23&to=2026-11-23");
        assertThat(response.body()).doesNotContain(TECNICO, usuario, "occupancies", "expiresAt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/professionals/0/availability?date=2026-11-23",
            "/api/professionals/-1/availability?date=2026-11-23", "/api/professionals/abc/availability?date=2026-11-23",
            "/api/professionals/42/availability", "/api/professionals/42/availability?date=2026-02-30",
            "/api/professionals/42/availability?date=ayer", "/api/professionals/42/availability?date=2026-11-21"})
    void rechazaEntradasInvalidasAntesDeLlamarDependencias(String path) throws Exception {
        error(get(path, token("valido")), 400, "INVALID_REQUEST");
        assertThat(CAT_REQUESTS).hasValue(0); assertThat(CATEDRA_REQUESTS).hasValue(0);
    }

    @ParameterizedTest
    @MethodSource("erroresCatalogo")
    void conservaErroresContractualesDeCatalogoSinConsultarOcupaciones(int status, String code, int expected, String expectedCode) throws Exception {
        CAT_STATUS.set(status); CAT_BODY.set("{\"code\":\"" + code + "\"}");
        error(get(PATH, token("valido")), expected, expectedCode);
        assertThat(CATEDRA_REQUESTS).hasValue(0);
    }

    static Stream<Arguments> erroresCatalogo() {
        return Stream.of(Arguments.of(404, "PROFESSIONAL_NOT_FOUND", 404, "PROFESSIONAL_NOT_FOUND"),
                Arguments.of(409, "PROFESSIONAL_DISABLED", 409, "PROFESSIONAL_DISABLED"),
                Arguments.of(503, "CATALOG_NOT_READY", 503, "CATALOG_NOT_READY"),
                Arguments.of(503, "", 503, "CATALOG_UNAVAILABLE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12:00", "13:00"})
    void agendaInvertidaODeDuracionCeroNoGeneraTurnos(String start) throws Exception {
        CAT_BODY.set(CatalogoClientTest.AGENDA.replace("\"startTime\":\"09:00\"", "\"startTime\":\"" + start + "\""));
        error(get(PATH, token("valido")), 502, "CATALOG_INVALID_RESPONSE");
        assertThat(CATEDRA_REQUESTS).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 503})
    void rechazoTecnicoNoSePresentaComoProblemaDelLoginDelUsuario(int status) throws Exception {
        CATEDRA_STATUS.set(status); CATEDRA_BODY.set("{\"detail\":\"" + TECNICO + "\"}");
        error(get(PATH, token("valido")), 503, "OCCUPANCIES_UNAVAILABLE");
    }

    @Test
    void respuestaDeOcupacionesIncorrectaNoInventaDisponibilidad() throws Exception {
        CATEDRA_BODY.set(OCUPADAS.replace("\"professionalId\":42", "\"professionalId\":43"));
        error(get(PATH, token("valido")), 502, "OCCUPANCIES_INVALID_RESPONSE");
    }

    @Test
    void aplicaTimeoutRealDeCatedra() throws Exception {
        DEMORA.set(700);
        error(get(PATH, token("valido")), 503, "OCCUPANCIES_UNAVAILABLE");
    }

    @Test
    void agendaSinHorariosDelDiaDevuelveVacioSinConsultarCatedra() throws Exception {
        CAT_BODY.set(CatalogoClientTest.AGENDA.replace("MONDAY", "TUESDAY"));
        var response = get(PATH, token("valido"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("availableSlots").size()).isZero();
        assertThat(CATEDRA_REQUESTS).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "invalido", "vencido", "otro-rol"})
    void requiereJwtDeUsuarioAntesDeInvocarDependencias(String caso) throws Exception {
        var response = get(PATH, token(caso));
        assertThat(response.statusCode()).isEqualTo(caso.equals("otro-rol") ? 403 : 401);
        assertThat(CAT_REQUESTS).hasValue(0); assertThat(CATEDRA_REQUESTS).hasValue(0);
    }

    private String token(String caso) {
        if (caso.equals("ausente")) return null;
        if (caso.equals("invalido")) return "token-invalido";
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("https://reservas.test").subject("42")
                .audience(List.of("turnos-reservas-test", "turnos-catalogo"))
                .issuedAt(now.minusSeconds(caso.equals("vencido") ? 600 : 5))
                .expiresAt(now.plusSeconds(caso.equals("vencido") ? -120 : 1800))
                .claim("roles", List.of(caso.equals("otro-rol") ? "ROLE_STUDENT_CLIENT" : "ROLE_USER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo(code);
        assertThat(response.body()).doesNotContain(TECNICO, "jdbc:", "stackTrace", "password");
    }

    private static HttpServer servidor(boolean tecnico) {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(EXECUTOR);
            if (tecnico) {
                server.createContext("/api/student/integration", exchange -> responder(exchange, 200,
                        "{\"groupId\":\"prueba\",\"provisioningStatus\":\"PENDING\"}"));
                server.createContext("/api/appointment-occupancies", exchange -> {
                    CATEDRA_REQUESTS.incrementAndGet();
                    CATEDRA_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    CATEDRA_QUERY.set(exchange.getRequestURI().getRawQuery());
                    try { Thread.sleep(DEMORA.get()); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    responder(exchange, CATEDRA_STATUS.get(), CATEDRA_BODY.get());
                });
            } else {
                server.createContext("/api/professionals", exchange -> {
                    CAT_REQUESTS.incrementAndGet(); CAT_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    responder(exchange, CAT_STATUS.get(), CAT_BODY.get());
                });
            }
            server.start();
            return server;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    private static void responder(HttpExchange exchange, int status, String body) throws IOException {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
        } finally { exchange.close(); }
    }

    private static String base(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private static Path tokenFile() {
        try {
            var path = Files.createTempFile("res07-token-", ".jwt");
            Files.writeString(path, TECNICO); path.toFile().deleteOnExit(); return path;
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
}
