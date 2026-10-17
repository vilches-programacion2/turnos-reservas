package ar.edu.um.turnos.reservas.procesoreserva.infrastructure.persistence.repository;

import ar.edu.um.turnos.reservas.procesoreserva.infrastructure.persistence.entity.ProcesoReservaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcesoReservaRepository extends JpaRepository<ProcesoReservaEntity, Long> {
    Optional<ProcesoReservaEntity> findByIdAndUserId(Long id, Long userId);
    Optional<ProcesoReservaEntity> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);
}
