package reservas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void healthReflectsDatabaseFailureWithoutExposingDetailsOrOpeningBusinessEndpoints() throws Exception {
        assertHealth("/actuator/health", 200, "UP");
        assertHealth("/actuator/health/readiness", 200, "UP");
        assertHealth("/actuator/health/liveness", 200, "UP");
        assertThat(get("/api/users").statusCode()).isEqualTo(403);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(403);
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(403);

        DATABASE.stop();
        assertHealth("/actuator/health/readiness", 503, "DOWN");
        // Una caída de PostgreSQL impide recibir tráfico, pero no mata el proceso.
        assertHealth("/actuator/health/liveness", 200, "UP");
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
}
