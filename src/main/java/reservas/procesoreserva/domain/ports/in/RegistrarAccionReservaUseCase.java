package reservas.procesoreserva.domain.ports.in;

import reservas.procesoreserva.domain.model.AccionReserva;

public interface RegistrarAccionReservaUseCase {
    void registrar(AccionReserva accion);
}
