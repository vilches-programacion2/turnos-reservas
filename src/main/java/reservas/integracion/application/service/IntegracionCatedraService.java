package reservas.integracion.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.integracion.domain.model.IntegracionCatedra;
import reservas.integracion.domain.ports.in.ObtenerIntegracionCatedraUseCase;
import reservas.integracion.domain.ports.in.PrepararIntegracionCatedraUseCase;
import reservas.integracion.domain.model.EstadoProvisionamiento;

@Service
@RequiredArgsConstructor
public class IntegracionCatedraService {
    private final ObtenerIntegracionCatedraUseCase obtenerIntegracion;
    private final PrepararIntegracionCatedraUseCase prepararIntegracion;

    public IntegracionCatedra obtener() {
        return obtenerIntegracion.obtener();
    }

    public EstadoProvisionamiento preparar() {
        return prepararIntegracion.preparar();
    }
}
