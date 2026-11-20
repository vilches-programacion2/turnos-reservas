package reservas.procesoreserva.infrastructure.persistence.repository;

import reservas.procesoreserva.infrastructure.persistence.entity.ProcesoReservaEntity;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaProcesoReservaRepository extends JpaRepository<ProcesoReservaEntity, Long> {
    Optional<ProcesoReservaEntity> findByIdAndUserId(Long id, Long userId);
    Optional<ProcesoReservaEntity> findByReservationProcessId(String reservationProcessId);
    List<ProcesoReservaEntity> findByUserIdAndReservationProcessIdIn(Long userId, Collection<String> ids);
    Optional<ProcesoReservaEntity> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId);

    @Query("""
            SELECT p FROM ProcesoReservaEntity p WHERE p.id > :desdeId AND (
                p.status IN ('CONFIRM_UNKNOWN', 'CANCEL_UNKNOWN', 'WAITING_FOR_PHONE', 'PHONE_REJECTED')
                OR (p.status = 'HELD' AND p.expiresAt <= :ahora)
                OR (p.status IN ('CREATING', 'CONFIRMING', 'CANCELLING') AND p.updatedAt <= :enCursoDesde)
            )
            ORDER BY p.id
            """)
    List<ProcesoReservaEntity> buscarPendientesDeRecuperacion(Long desdeId, Instant ahora, Instant enCursoDesde, Pageable pagina);
}
