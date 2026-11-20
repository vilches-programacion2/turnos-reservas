package reservas;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.application.exception.ConsultaCatalogoException.Error;
import reservas.catalogo.infrastructure.client.adapter.CatalogoHttpAdapter;
import reservas.catalogo.infrastructure.client.mapper.CatalogoMapper;
import reservas.catalogo.infrastructure.config.CatalogoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoTransportTest {
    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noSigueRedireccionesQuePodrianExponerElJwt() throws Exception {
        var capturadas = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/professionals/42", exchange -> {
            exchange.getResponseHeaders().add("Location", "/captura");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/captura", exchange -> {
            capturadas.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (var context = contexto(server, "1s")) {
            assertError(context, Error.ERROR_CONTRATO);
            assertThat(capturadas).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutImpideContinuarLaConsultaSinRespuestaVigente() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/professionals/42", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (var context = contexto(server, "50ms")) {
            assertError(context, Error.NO_DISPONIBLE);
        } finally {
            server.stop(0);
        }
    }

    private AnnotationConfigApplicationContext contexto(HttpServer server, String timeout) {
        CatalogoClientTest.autenticar(java.util.List.of("turnos-reservas", "turnos-catalogo"), "ROLE_USER");
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.catalogo.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                "app.catalogo.audience", "turnos-catalogo", "app.catalogo.connect-timeout", "1s",
                "app.catalogo.read-timeout", timeout)));
        context.register(CatalogoConfiguration.class, CatalogoHttpAdapter.class, CatalogoMapper.class);
        context.refresh();
        return context;
    }

    private void assertError(AnnotationConfigApplicationContext context, Error error) {
        assertThatThrownBy(() -> context.getBean(CatalogoHttpAdapter.class).consultarProfesional(42L))
                .isInstanceOfSatisfying(ConsultaCatalogoException.class, e -> assertThat(e.getError()).isEqualTo(error));
    }
}
