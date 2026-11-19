package reservas.procesoreserva.infrastructure.persistence.repository;

import reservas.procesoreserva.infrastructure.persistence.entity.ProcesoReservaEntity;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcesoReservaRepository extends JpaRepository<ProcesoReservaEntity, Long> {
    Optional<ProcesoReservaEntity> findByIdAndUserId(Long id, Long userId);
    Optional<ProcesoReservaEntity> findByReservationProcessId(String reservationProcessId);
    List<ProcesoReservaEntity> findByUserIdAndReservationProcessIdIn(Long userId, Collection<String> ids);
    Optional<ProcesoReservaEntity> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);
}
