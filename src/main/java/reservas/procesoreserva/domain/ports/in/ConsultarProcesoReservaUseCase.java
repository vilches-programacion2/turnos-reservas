package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.ProcesoReserva;

public interface ConsultarProcesoReservaUseCase {
    ProcesoReserva consultar(Long userId, Long id);
}
