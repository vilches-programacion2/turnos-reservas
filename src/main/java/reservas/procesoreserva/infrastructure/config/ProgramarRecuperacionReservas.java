package reservas.procesoreserva.infrastructure.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import reservas.procesoreserva.application.service.RecuperacionReservasService;
import reservas.shared.infrastructure.catedra.CatedraProperties;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Order(20)
@RequiredArgsConstructor
public class ProgramarRecuperacionReservas implements ApplicationRunner {
    private static final Duration MARGEN_OPERACION = Duration.ofSeconds(30);

    private final RecuperacionReservasService recuperacion;
    private final CatedraProperties catedra;

    @Override
    public void run(ApplicationArguments args) {
        ejecutar();
    }

    @Scheduled(fixedDelayString = "${app.reservas.recuperacion.intervalo}",
            initialDelayString = "${app.reservas.recuperacion.intervalo}")
    public void ejecutar() {
        var espera = catedra.getConnectTimeout().plus(catedra.getReadTimeout()).plus(MARGEN_OPERACION);
        recuperacion.ejecutar(catedra.isEnabled(), espera);
    }
}
