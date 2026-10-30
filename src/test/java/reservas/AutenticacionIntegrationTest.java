package reservas;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.UsuarioRepository;
import reservas.usuario.infrastructure.security.config.JwtProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutenticacionIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        JwtTestKeys.configurar(registry);
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort private int port;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtProperties properties;

    private static final String PASSWORD = " Clave de prueba con espacios ";
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void registroLoginYConsultaDeCuentaConJwtPropio() throws Exception {
        var usuario = registrar();
        var response = autenticar(Map.of("username", usuario.getLogin().toUpperCase(Locale.ROOT), "password", PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
        String token = json.readTree(response.body()).get("id_token").asString();
        var claims = decoder.decode(token);
        assertThat(claims.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(claims.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(claims.getAudience()).containsExactly(properties.audience());
        assertThat(claims.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(Duration.between(claims.getIssuedAt(), claims.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(claims.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");

        var cuenta = cuenta("/api/account", token);
        assertThat(cuenta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cuenta.body()).get("id").asLong()).isEqualTo(usuario.getId());
        assertThat(cuenta.body()).doesNotContain("password", usuario.getPasswordHash());
    }

    @Test
    void laIdentidadSaleDelTokenYNoDeIdsEnviadosPorElCliente() throws Exception {
        var primero = registrar();
        var segundo = registrar();
        var response = autenticar(Map.of("username", primero.getLogin(), "password", PASSWORD,
                "userId", segundo.getId(), "authorities", List.of("ROLE_ADMIN")));
        assertThat(response.statusCode()).isEqualTo(200);
        String token = json.readTree(response.body()).get("id_token").asString();
        var propia = cuenta("/api/account?userId=" + segundo.getId(), token);
        assertThat(json.readTree(propia.body()).get("id").asLong()).isEqualTo(primero.getId());
        var otra = cuenta("/api/account", login(segundo));
        assertThat(json.readTree(otra.body()).get("id").asLong()).isEqualTo(segundo.getId());
        assertThat(cuenta("/api/account/" + segundo.getId(), token).statusCode()).isEqualTo(403);

        String[] partes = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), java.nio.charset.StandardCharsets.UTF_8);
        String adulterado = payload.replace("\"sub\":\"" + primero.getId() + "\"",
                "\"sub\":\"" + segundo.getId() + "\"");
        assertThat(adulterado).isNotEqualTo(payload);
        String falso = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(adulterado.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + partes[2];
        assertThat(cuenta("/api/account", falso).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "inexistente", "inactivo"})
    void rechazaCredencialesSinRevelarSiExisteLaCuenta(String caso) throws Exception {
        var usuario = registrar();
        if ("inactivo".equals(caso)) {
            usuario.setActivated(false);
            usuarios.save(usuario);
        }
        var response = autenticar(Map.of("username", "inexistente".equals(caso) ? "no-existe" : usuario.getLogin(),
                "password", "password".equals(caso) ? "incorrecta" : PASSWORD));
        assertThat(response.statusCode()).isEqualTo(401);
        var error = json.readTree(response.body());
        assertThat(error.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(error.get("detail").asString()).isEqualTo("Credenciales inválidas.");
        assertThat(error.has("id_token")).isFalse();
        assertThat(response.body()).doesNotContain(usuario.getLogin(), PASSWORD, usuario.getPasswordHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"username\":\"demo\"}",
            "{\"username\":\"demo\",\"password\":\"123\"}", "{\"username\":\"\",\"password\":\"clave\"}"})
    void validaElLoginSinExponerEntradas(String body) throws Exception {
        var response = client.send(post("/api/authenticate", body), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("INVALID_REQUEST");
        assertThat(response.body()).doesNotContain("rejectedValue", "clave", "123");
    }

    @ParameterizedTest
    @MethodSource("tokensRechazados")
    void validaFirmaVigenciaOrigenIdentidadYPermisos(String caso, int estado) throws Exception {
        var usuario = registrar();
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(usuario.getId().toString()).issuedAt(ahora.minusSeconds(5))
                .expiresAt(ahora.plusSeconds(300)).claim("roles", List.of("ROLE_USER"));
        SignatureAlgorithm algoritmo = SignatureAlgorithm.RS256;
        JwtEncoder firma = encoder;
        switch (caso) {
            case "vencido" -> claims.issuedAt(ahora.minusSeconds(600)).expiresAt(ahora.minusSeconds(120));
            case "futuro" -> claims.notBefore(ahora.plusSeconds(300));
            case "emisor" -> claims.issuer("otro-emisor");
            case "audiencia" -> claims.audience(List.of("otro-servicio"));
            case "sin-exp" -> claims.claims(values -> values.remove("exp"));
            case "sin-iat" -> claims.claims(values -> values.remove("iat"));
            case "sin-sub" -> claims.claims(values -> values.remove("sub"));
            case "sub-invalido" -> claims.subject("no-es-un-id");
            case "rol" -> claims.claim("roles", List.of("ROLE_ADMIN"));
            case "sin-roles" -> claims.claims(values -> values.remove("roles"));
            case "roles-invalidos" -> claims.claim("roles", List.of(123));
            case "algoritmo" -> algoritmo = SignatureAlgorithm.RS512;
            case "otra-clave" -> firma = otroEmisor();
            default -> throw new IllegalArgumentException(caso);
        }
        String token = firma.encode(JwtEncoderParameters.from(JwsHeader.with(algoritmo).build(), claims.build())).getTokenValue();
        assertThat(cuenta("/api/account", token).statusCode()).isEqualTo(estado);
    }

    static Stream<Arguments> tokensRechazados() {
        return Stream.of("vencido", "futuro", "emisor", "audiencia", "sin-exp", "sin-iat", "sin-sub",
                        "sub-invalido", "rol", "sin-roles", "roles-invalidos", "algoritmo", "otra-clave")
                .map(caso -> Arguments.of(caso, List.of("rol", "sin-roles").contains(caso) ? 403 : 401));
    }

    @Test
    void rechazaCuentaInactivaYTokensSinUsuarioLocal() throws Exception {
        var usuario = registrar();
        String token = login(usuario);
        usuario.setActivated(false);
        usuarios.save(usuario);
        assertThat(cuenta("/api/account", token).statusCode()).isEqualTo(401);
        String sinUsuario = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                        .subject(Long.toString(Long.MAX_VALUE)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                        .claim("roles", List.of("ROLE_USER")).build())).getTokenValue();
        assertThat(cuenta("/api/account", sinUsuario).statusCode()).isEqualTo(401);
    }

    @Test
    void exigeBearerYConservaCerradasOtrasOperaciones() throws Exception {
        for (String autorizacion : List.of("", "Bearer no-es-un-jwt", "Basic ZGVtbzpjbGF2ZQ==")) {
            var request = HttpRequest.newBuilder(uri("/api/account"));
            if (!autorizacion.isEmpty()) {
                request.header("Authorization", autorizacion);
            }
            assertThat(client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
        String token = login(registrar());
        assertThat(cuenta("/api/users", token).statusCode()).isEqualTo(403);
        assertThat(cuenta("/actuator/env", token).statusCode()).isEqualTo(403);
    }

    private Usuario registrar() throws Exception {
        String login = "user-" + UUID.randomUUID();
        var body = Map.of("login", login, "password", PASSWORD, "firstName", "Juan", "lastName", "Pérez",
                "email", login + "@example.com", "langKey", "es");
        var response = client.send(post("/api/register", json.writeValueAsString(body)), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return usuarios.findByLoginIgnoreCase(login).orElseThrow();
    }

    private String login(Usuario usuario) throws Exception {
        var response = autenticar(Map.of("username", usuario.getLogin(), "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("id_token").asString();
    }

    private HttpResponse<String> autenticar(Map<String, Object> body) throws Exception {
        return client.send(post("/api/authenticate", json.writeValueAsString(body)), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest post(String path, String body) {
        return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpResponse<String> cuenta(String path, String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JwtEncoder otroEmisor() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
