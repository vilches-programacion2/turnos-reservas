package reservas.procesoreserva.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.service.ResultadosReservaService;

@Component
@Order(0)
@RequiredArgsConstructor
public class RecuperarResultadosGuardados implements ApplicationRunner {
    private final ResultadosReservaService resultados;

    @Override
    public void run(ApplicationArguments args) {
        resultados.recuperarGuardados();
    }
}
