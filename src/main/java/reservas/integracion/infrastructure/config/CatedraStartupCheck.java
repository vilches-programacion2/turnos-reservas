package reservas.integracion.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.service.IntegracionCatedraService;
import reservas.integracion.domain.model.EstadoProvisionamiento;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.catedra", name = "enabled", havingValue = "true")
public class CatedraStartupCheck implements ApplicationRunner {
    private final IntegracionCatedraService integracion;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var estado = integracion.preparar();
            log.info("Integración de cátedra consultada: estado={} kafka={}", estado,
                    estado == EstadoProvisionamiento.PROVISIONED ? "DISPONIBLE" : "NO_HABILITADO");
        } catch (IntegracionCatedraException e) {
            // Registro, login y cuenta siguen disponibles aunque falle la integración externa.
            log.warn("No se pudo consultar integración de cátedra: error={} httpStatus={}",
                    e.getError(), e.getHttpStatus());
        }
    }
}
