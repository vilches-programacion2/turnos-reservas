package reservas.integracion.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.integracion.domain.model.EstadoProvisionamiento;
import reservas.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import reservas.integracion.domain.ports.in.PrepararIntegracionCatedraUseCase;
import reservas.integracion.domain.ports.out.PrepararKafkaCatedraPort;

@Component
@RequiredArgsConstructor
public class PrepararIntegracionCatedraUseCaseImpl implements PrepararIntegracionCatedraUseCase {
    private final ObtenerIntegracionCatedraUseCase obtenerIntegracion;
    private final PrepararKafkaCatedraPort kafka;

    @Override
    public EstadoProvisionamiento preparar() {
        var integracion = obtenerIntegracion.obtener();
        kafka.preparar(integracion);
        return integracion.getProvisioningStatus();
    }
}
