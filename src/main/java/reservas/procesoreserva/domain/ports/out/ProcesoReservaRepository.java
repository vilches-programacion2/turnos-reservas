package reservas.procesoreserva.domain.ports.out;

import reservas.procesoreserva.domain.model.ProcesoReserva;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ProcesoReservaRepository {
    ProcesoReserva save(ProcesoReserva value);
    Optional<ProcesoReserva> findByIdAndUserId(Long id, Long userId);
    Optional<ProcesoReserva> findByReservationProcessId(String reservationProcessId);
    List<ProcesoReserva> findByUserIdAndReservationProcessIdIn(Long userId, Collection<String> ids);
    Optional<ProcesoReserva> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);
}
