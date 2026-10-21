package ar.edu.um.turnos.reservas.procesoreserva.domain.ports.out;

import ar.edu.um.turnos.reservas.procesoreserva.domain.model.ProcesoReserva;
import java.util.Optional;

public interface ProcesoReservaRepository {
    ProcesoReserva save(ProcesoReserva value);
    Optional<ProcesoReserva> findByIdAndUserId(Long id, Long userId);
    Optional<ProcesoReserva> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);
}
