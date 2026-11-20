package reservas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.hikari.connection-timeout=1000",
                "spring.datasource.hikari.validation-timeout=500"})
class ServiceBootstrapTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;
    @Autowired
    JsonMapper json;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void healthReflectsDatabaseFailureWithoutExposingDetailsOrOpeningBusinessEndpoints() throws Exception {
        assertHealth("/actuator/health", 200, "UP");
        assertHealth("/actuator/health/readiness", 200, "UP");
        assertHealth("/actuator/health/liveness", 200, "UP");
        assertThat(get("/api/users").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(401);
        assertThat(context.getBeansOfType(org.springframework.kafka.core.KafkaTemplate.class)).isEmpty();
        assertThat(context.getBeansOfType(org.springframework.kafka.core.KafkaAdmin.class)).isEmpty();
        assertThat(context.getBeansOfType(org.springframework.kafka.listener.MessageListenerContainer.class)).isEmpty();

        assertThat(post("/api/register", """
                {"login":"prueba-res14","password":"clave-ficticia-res14","firstName":"Ana",
                 "lastName":"Prueba","email":"res14@example.test","langKey":"es"}
                """).statusCode()).isEqualTo(201);
        var login = post("/api/authenticate", """
                {"username":"prueba-res14","password":"clave-ficticia-res14"}
                """);
        assertThat(login.statusCode()).isEqualTo(200);
        var token = json.readTree(login.body()).path("id_token").asString();
        assertThat(get("/api/account", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/appointment-holds/1", token).statusCode()).isEqualTo(404);

        DATABASE.stop();
        assertHealth("/actuator/health/readiness", 503, "DOWN");
        // Una caída de PostgreSQL impide recibir tráfico, pero no mata el proceso.
        assertHealth("/actuator/health/liveness", 200, "UP");
        for (String path : new String[] {"/api/account", "/api/appointment-holds/1"}) {
            var respuesta = get(path, token);
            assertThat(respuesta.statusCode()).as("Estado HTTP de %s sin PostgreSQL", path).isEqualTo(503);
            assertThat(respuesta.body()).as("Respuesta de %s sin PostgreSQL", path).contains("RESERVAS_UNAVAILABLE")
                    .doesNotContain("jdbc:", "password", "stackTrace", token);
        }
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertHealth(String path, int status, String state) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).contains("\"status\":\"" + state + "\"")
                .doesNotContain("components", "details", "jdbc:", "password");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
