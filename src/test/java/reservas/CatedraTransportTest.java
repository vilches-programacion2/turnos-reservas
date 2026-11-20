package reservas;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.application.service.IntegracionCatedraService;
import reservas.integracion.application.usecases.ObtenerIntegracionCatedraUseCaseImpl;
import reservas.integracion.application.usecases.PrepararIntegracionCatedraUseCaseImpl;
import reservas.integracion.domain.ports.out.PrepararKafkaCatedraPort;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.infrastructure.client.adapter.CatedraHttpAdapter;
import reservas.integracion.infrastructure.client.mapper.IntegracionCatedraMapper;
import reservas.shared.infrastructure.catedra.CatedraConfiguration;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatedraTransportTest {
    @TempDir Path temp;

    @Test
    void clienteRealNoSigueRedireccionesNiReenviaElToken() throws Exception {
        var recibidas = new AtomicInteger();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/student/integration", exchange -> {
            exchange.getResponseHeaders().add("Location", "/captura");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        http.createContext("/captura", exchange -> {
            recibidas.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        http.start();
        try (var context = context(url(http), tokenFile(), "1s")) {
            assertThatThrownBy(() -> context.getBean(IntegracionCatedraService.class).obtener())
                    .isInstanceOfSatisfying(IntegracionCatedraException.class, e -> {
                        assertThat(e.getError()).isEqualTo(Error.ERROR_HTTP);
                        assertThat(e.getHttpStatus()).isEqualTo(302);
                    });
            assertThat(recibidas).hasValue(0);
        } finally {
            http.stop(0);
        }
    }

    @Test
    void clienteRealAplicaTimeoutDeLectura() throws Exception {
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/student/integration", exchange -> {
            try {
                Thread.sleep(Duration.ofMillis(500));
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        http.start();
        try (var context = context(url(http), tokenFile(), "50ms")) {
            assertThatThrownBy(() -> context.getBean(IntegracionCatedraService.class).obtener())
                    .isInstanceOfSatisfying(IntegracionCatedraException.class,
                            e -> assertThat(e.getError()).isEqualTo(Error.NO_DISPONIBLE));
        } finally {
            http.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CAT04_LIVE", matches = "true")
    void consultaRealOptativaConConfiguracionLocalSinPublicarSecretos() {
        try (var context = context(System.getenv("CATEDRA_BASE_URL"),
                Path.of(System.getenv("CATEDRA_TOKEN_FILE")), "5s")) {
            var integration = context.getBean(IntegracionCatedraService.class).obtener();
            assertThat(integration.getProvisioningStatus()).isEqualTo(EstadoProvisionamiento.PROVISIONED);
            assertThat(integration.getRedisPassword()).isNotBlank();
            assertThat(integration.getKafkaCatalogTopic()).isNotBlank();
        }
    }

    private AnnotationConfigApplicationContext context(String url, Path token, String timeout) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.catedra.enabled", "true", "app.catedra.base-url", url,
                "app.catedra.token-file", token.toString(), "app.catedra.read-timeout", timeout)));
        context.registerBean(PrepararKafkaCatedraPort.class, () -> integracion -> { });
        context.register(PrepararIntegracionCatedraUseCaseImpl.class, CatedraConfiguration.class, CatedraTokenFile.class, CatedraHttpAdapter.class, IntegracionCatedraMapper.class,
                ObtenerIntegracionCatedraUseCaseImpl.class, IntegracionCatedraService.class);
        context.refresh();
        return context;
    }

    private Path tokenFile() throws Exception {
        var file = temp.resolve("catedra.jwt");
        Files.writeString(file, "token-simulado");
        return file;
    }

    private String url(HttpServer http) {
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }
}
