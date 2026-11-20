package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.EstadoTelefono;

public interface EnviarTelefonoUseCase {
    EstadoTelefono enviar(Long userId, Long id, String telefono);
}
