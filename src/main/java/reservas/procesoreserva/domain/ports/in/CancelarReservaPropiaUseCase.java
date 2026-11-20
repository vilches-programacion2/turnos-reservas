package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.ProcesoReserva;

public interface CancelarReservaPropiaUseCase {
    ProcesoReserva cancelar(Long userId, Long id, String reason);
}
