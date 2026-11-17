package reservas.integracion.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.integracion.domain.model.IntegracionCatedra;
import reservas.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import reservas.integracion.domain.ports.out.CatedraClient;

@Component
@RequiredArgsConstructor
public class ObtenerIntegracionCatedraUseCaseImpl implements ObtenerIntegracionCatedraUseCase {
    private final CatedraClient catedra;

    @Override
    public IntegracionCatedra obtener() {
        return catedra.obtenerIntegracion();
    }
}
