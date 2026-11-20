package reservas;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reservas.usuario.domain.ports.out.PasswordHasher;
import reservas.usuario.domain.ports.out.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistroUsuarioIntegrationTest {

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
    private int port;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordHasher passwordHasher;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void registraSinAutenticacionYElBackendControlaLosCamposInternos() throws Exception {
        Map<String, Object> datos = registro();
        datos.put("imageUrl", "https://example.com/avatar.png");
        datos.put("id", 999999L);
        datos.put("activated", false);
        datos.put("authorities", List.of("ROLE_ADMIN"));
        datos.put("passwordHash", "hash-elegido-por-cliente");
        datos.put("createdAt", "2000-01-01T00:00:00Z");
        datos.put("updatedAt", "2000-01-01T00:00:00Z");
        Instant antes = Instant.now();

        HttpResponse<String> response = registrar(datos);

        assertThat(response.statusCode()).isEqualTo(201);
        var body = json.readTree(response.body());
        var usuario = usuarios.findByLoginIgnoreCase((String) datos.get("login")).orElseThrow();
        assertThat(body.get("id").asLong()).isEqualTo(usuario.getId()).isNotEqualTo(999999L);
        assertThat(body.get("activated").asBoolean()).isTrue();
        for (String campo : List.of("login", "firstName", "lastName", "email", "imageUrl", "langKey")) {
            assertThat(body.get(campo).asText()).isEqualTo(datos.get(campo));
        }
        assertThat(usuario.isActivated()).isTrue();
        assertThat(usuario.getAuthorities()).containsExactly("ROLE_USER");
        assertThat(usuario.getCreatedAt()).isAfterOrEqualTo(antes);
        assertThat(usuario.getUpdatedAt()).isEqualTo(usuario.getCreatedAt());
        assertThat(passwordHasher.matches((String) datos.get("password"), usuario.getPasswordHash())).isTrue();
        assertThat(response.body()).doesNotContain("password", "hash-elegido-por-cliente", usuario.getPasswordHash());
        assertThat(body.has("id_token")).isFalse();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 100})
    void aceptaLimitesDePasswordSinImagenOpcional(int longitud) throws Exception {
        Map<String, Object> datos = registro();
        datos.put("password", "x".repeat(longitud));
        assertThat(registrar(datos).statusCode()).isEqualTo(201);
        var usuario = usuarios.findByLoginIgnoreCase((String) datos.get("login")).orElseThrow();
        assertThat(usuario.getImageUrl()).isNull();
        assertThat(passwordHasher.matches((String) datos.get("password"), usuario.getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @MethodSource("camposInvalidos")
    void rechazaCamposInvalidosSinPersistirNiExponerValores(String campo, Object valor) throws Exception {
        Map<String, Object> datos = registro();
        String login = (String) datos.get("login");
        String email = (String) datos.get("email");
        datos.put(campo, valor);

        HttpResponse<String> response = registrar(datos);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        var body = json.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("fieldErrors").toString()).contains("\"field\":\"" + campo + "\"");
        assertThat(response.body()).doesNotContain("rejectedValue", email);
        assertThat(usuarios.findByLoginIgnoreCase(login)).isEmpty();
    }

    static Stream<Arguments> camposInvalidos() {
        Stream<Arguments> ausentes = Stream.of("login", "password", "firstName", "lastName", "email", "langKey")
                .map(campo -> Arguments.of(campo, null));
        Stream<Arguments> limites = Stream.of(
                Arguments.of("login", " "),
                Arguments.of("login", "con espacios"),
                Arguments.of("login", "x".repeat(51)),
                Arguments.of("password", "123"),
                Arguments.of("password", " ".repeat(4)),
                Arguments.of("password", "x".repeat(101)),
                Arguments.of("firstName", " "),
                Arguments.of("firstName", "x".repeat(51)),
                Arguments.of("lastName", " "),
                Arguments.of("lastName", "x".repeat(51)),
                Arguments.of("email", "invalido"),
                Arguments.of("email", "a@b"),
                Arguments.of("email", "x".repeat(255)),
                Arguments.of("langKey", "e"),
                Arguments.of("langKey", "x".repeat(11)),
                Arguments.of("imageUrl", "x".repeat(257)));
        return Stream.concat(ausentes, limites);
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "email"})
    void rechazaDuplicadosInclusoConMayusculas(String campo) throws Exception {
        Map<String, Object> original = registro();
        assertThat(registrar(original).statusCode()).isEqualTo(201);
        Map<String, Object> duplicado = registro();
        duplicado.put(campo, ((String) original.get(campo)).toUpperCase(Locale.ROOT));

        HttpResponse<String> response = registrar(duplicado);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(codigoDuplicado(campo));
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "email"})
    void dosRegistrosSimultaneosNoDuplicanLaCuenta(String campo) throws Exception {
        Map<String, Object> primero = registro();
        Map<String, Object> segundo = registro();
        segundo.put(campo, primero.get(campo));

        var a = client.sendAsync(request("/api/register", json.writeValueAsString(primero)),
                HttpResponse.BodyHandlers.ofString());
        var b = client.sendAsync(request("/api/register", json.writeValueAsString(segundo)),
                HttpResponse.BodyHandlers.ofString());
        var respuestas = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));

        assertThat(respuestas).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 400);
        var conflicto = respuestas.stream().filter(response -> response.statusCode() == 400).findFirst().orElseThrow();
        assertThat(json.readTree(conflicto.body()).get("code").asText()).isEqualTo(codigoDuplicado(campo));
    }

    @Test
    void rechazaJsonInvalidoYNoAbreOtrasOperaciones() throws Exception {
        for (String body : List.of("{", "", "null", "{\"password\": {\"valor\": \"secreto-invalido\"}}")) {
            var response = client.send(request("/api/register", body), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(response.body()).doesNotContain("secreto-invalido");
        }
        var get = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/register")).GET().build();
        assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(client.send(request("/api/users", "{}"), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(401);
    }

    private Map<String, Object> registro() {
        String login = "user-" + UUID.randomUUID();
        return new HashMap<>(Map.of("login", login, "password", " Una clave de prueba sin recortar ",
                "firstName", "Juan", "lastName", "Pérez", "email", login + "@example.com", "langKey", "es"));
    }

    private HttpResponse<String> registrar(Map<String, Object> datos) throws Exception {
        return client.send(request("/api/register", json.writeValueAsString(datos)), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private String codigoDuplicado(String campo) {
        return "login".equals(campo) ? "USERNAME_ALREADY_EXISTS" : "EMAIL_ALREADY_EXISTS";
    }
}
