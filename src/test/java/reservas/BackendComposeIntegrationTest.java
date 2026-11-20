package reservas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.application.exception.ConsultaCatalogoException.Error;
import reservas.catalogo.application.service.CatalogoService;
import reservas.catalogo.application.usecases.ConsultarAgendaProfesionalUseCaseImpl;
import reservas.catalogo.application.usecases.ConsultarProfesionalUseCaseImpl;
import reservas.catalogo.infrastructure.client.adapter.CatalogoHttpAdapter;
import reservas.catalogo.infrastructure.client.mapper.CatalogoMapper;
import reservas.catalogo.infrastructure.config.CatalogoProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verificación optativa contra los procesos reales del Compose integral, sin secretos de cátedra. */
@EnabledIfEnvironmentVariable(named = "BACKEND_COMPOSE_TEST", matches = "true")
class BackendComposeIntegrationTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void verificaAmbosBackendsYElClienteRealConElJwtEmitidoPorReservas() throws Exception {
        String reservas = variable("RESERVAS_TEST_URL", "http://reservas:8080");
        String catalogo = variable("CATALOGO_TEST_URL", "http://catalogo:8080");
        boolean catalogoCaido = Boolean.parseBoolean(variable("BACKEND_EXPECT_CATALOGO_DOWN", "false"));
        esperarReadiness(reservas);
        if (!catalogoCaido) {
            esperarReadiness(catalogo);
        }

        String login = "res13-" + UUID.randomUUID();
        String password = UUID.randomUUID().toString();
        assertThat(post(reservas + "/api/register", Map.of("login", login, "password", password,
                "firstName", "Demo", "lastName", "Integracion", "email", login + "@example.com",
                "langKey", "es")).statusCode()).isEqualTo(201);
        var autenticacion = post(reservas + "/api/authenticate", Map.of("username", login, "password", password));
        assertThat(autenticacion.statusCode()).isEqualTo(200);
        String token = json.readTree(autenticacion.body()).path("id_token").asString();
        assertThat(token).isNotBlank();

        NimbusJwtDecoder decoder;
        try (var input = Files.newInputStream(Path.of(variable("JWT_PUBLIC_KEY_FILE", "/run/secrets/public.pem")))) {
            var publicKey = RsaKeyConverters.x509().convert(input);
            assertThat(publicKey).isNotNull();
            decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        }
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(variable("JWT_ISSUER", "https://turnos-reservas.local")));
        var jwt = decoder.decode(token);
        String audienciaCatalogo = variable("JWT_CATALOG_AUDIENCE", "turnos-catalogo");
        assertThat(jwt.getAudience()).contains(variable("JWT_AUDIENCE", "turnos-reservas"), audienciaCatalogo);
        assertThat(jwt.getClaimAsStringList("roles")).contains("ROLE_USER");
        assertThat(Long.parseLong(jwt.getSubject())).isPositive();
        assertThat(get(reservas + "/api/account", null).statusCode()).isEqualTo(401);
        var cuenta = get(reservas + "/api/account", token);
        assertThat(cuenta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cuenta.body()).path("id").asLong()).isEqualTo(Long.parseLong(jwt.getSubject()));
        assertThat(cuenta.body()).doesNotContain("password", "id_token");

        // Usa las clases de producción de Reservas y la red real; no un mock ni un proxy de demostración.
        var properties = new CatalogoProperties(URI.create(catalogo), audienciaCatalogo,
                Duration.ofSeconds(3), Duration.ofSeconds(5));
        var builder = RestClient.builder().baseUrl(catalogo);
        try (var httpInterno = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpInterno);
            factory.setReadTimeout(properties.readTimeout());
            var adapter = new CatalogoHttpAdapter(builder.requestFactory(factory).build(), properties, new CatalogoMapper());
            var service = new CatalogoService(new ConsultarProfesionalUseCaseImpl(adapter),
                    new ConsultarAgendaProfesionalUseCaseImpl(adapter));
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                    jwt.getClaimAsStringList("roles").stream().map(SimpleGrantedAuthority::new).toList()));
            try {
                if (catalogoCaido) {
                    assertThatThrownBy(() -> service.consultarAgenda(1L))
                            .isInstanceOfSatisfying(ConsultaCatalogoException.class,
                                    e -> assertThat(e.getError()).isEqualTo(Error.NO_DISPONIBLE));
                } else {
                    String endpoint = catalogo + "/api/professionals/1/schedule";
                    assertThat(get(endpoint, null).statusCode()).isEqualTo(401);
                    assertThat(get(endpoint, "token-invalido").statusCode()).isEqualTo(401);
                    var response = get(endpoint, token);
                    verificarRespuestaDelCliente(service, response);
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
        assertThat(get(reservas + "/api/account", token).statusCode()).isEqualTo(200);
    }

    private void verificarRespuestaDelCliente(CatalogoService service, HttpResponse<String> response) {
        if (response.statusCode() == 200) {
            var agenda = service.consultarAgenda(1L);
            assertThat(agenda.getProfessional().getId()).isEqualTo(1L);
            assertThat(agenda.getVersionAplicada()).isNotNegative();
            return;
        }
        String code = json.readTree(response.body()).path("code").asString();
        Error esperado;
        if (response.statusCode() == 503 && "CATALOG_NOT_READY".equals(code)) {
            esperado = Error.NO_INICIALIZADO;
        } else if (response.statusCode() == 404 && "PROFESSIONAL_NOT_FOUND".equals(code)) {
            esperado = Error.PROFESIONAL_NO_ENCONTRADO;
        } else if (response.statusCode() == 409 && "PROFESSIONAL_DISABLED".equals(code)) {
            esperado = Error.PROFESIONAL_DESHABILITADO;
        } else {
            throw new AssertionError("Respuesta inesperada de Catálogo: HTTP " + response.statusCode());
        }
        assertThatThrownBy(() -> service.consultarAgenda(1L))
                .isInstanceOfSatisfying(ConsultaCatalogoException.class, e -> assertThat(e.getError()).isEqualTo(esperado));
    }

    private void esperarReadiness(String base) throws Exception {
        Instant limite = Instant.now().plusSeconds(120);
        while (Instant.now().isBefore(limite)) {
            try {
                var response = get(base + "/actuator/health/readiness", null);
                if (response.statusCode() == 200 && "UP".equals(json.readTree(response.body()).path("status").asString())) {
                    return;
                }
            } catch (java.io.IOException e) {
                // El contenedor puede estar arrancando; no registrar cuerpos ni encabezados.
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Un backend no alcanzó readiness dentro del plazo de verificación.");
    }

    private HttpResponse<String> post(String url, Map<String, String> body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String variable(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }
}
