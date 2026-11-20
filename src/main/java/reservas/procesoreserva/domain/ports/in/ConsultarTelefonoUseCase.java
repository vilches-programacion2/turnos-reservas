package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.EstadoTelefono;

public interface ConsultarTelefonoUseCase {
    EstadoTelefono consultar(Long userId, Long id);
}
