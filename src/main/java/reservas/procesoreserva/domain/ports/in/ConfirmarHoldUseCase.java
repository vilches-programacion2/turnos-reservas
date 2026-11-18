package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.ProcesoReserva;

public interface ConfirmarHoldUseCase {
    ProcesoReserva confirmar(Long userId, Long id);
}
