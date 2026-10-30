package reservas.procesoreserva.infrastructure.persistence.adapter;

import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.infrastructure.persistence.repository.JpaProcesoReservaRepository;
import reservas.procesoreserva.infrastructure.persistence.mapper.ProcesoReservaMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaProcesoReservaRepositoryAdapter implements ProcesoReservaRepository {
    private final JpaProcesoReservaRepository repository;
    private final ProcesoReservaMapper mapper;

    @Override
    @Transactional
    public ProcesoReserva save(ProcesoReserva value) {
        return mapper.toDomainModel(repository.saveAndFlush(mapper.toEntity(value)));
    }

    @Override
    public Optional<ProcesoReserva> findByIdAndUserId(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId).map(mapper::toDomainModel);
    }

    @Override
    public Optional<ProcesoReserva> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId) {
        return repository.findByReservationProcessIdAndUserId(reservationProcessId, userId).map(mapper::toDomainModel);
    }
}
