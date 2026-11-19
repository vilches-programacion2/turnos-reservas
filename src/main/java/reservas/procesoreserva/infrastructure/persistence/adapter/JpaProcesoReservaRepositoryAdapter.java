package reservas.procesoreserva.infrastructure.persistence.adapter;

import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.infrastructure.persistence.repository.JpaProcesoReservaRepository;
import reservas.procesoreserva.infrastructure.persistence.mapper.ProcesoReservaMapper;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public Optional<ProcesoReserva> findByReservationProcessId(String reservationProcessId) {
        return repository.findByReservationProcessId(reservationProcessId).map(mapper::toDomainModel);
    }

    @Override
    public List<ProcesoReserva> findByUserIdAndReservationProcessIdIn(Long userId, Collection<String> ids) {
        return repository.findByUserIdAndReservationProcessIdIn(userId, ids).stream().map(mapper::toDomainModel).toList();
    }

    @Override
    public Optional<ProcesoReserva> findByReservationProcessIdAndUserId(String reservationProcessId, Long userId) {
        return repository.findByReservationProcessIdAndUserId(reservationProcessId, userId).map(mapper::toDomainModel);
    }

    @Override
    public List<ProcesoReserva> buscarPendientesDeRecuperacion(Long desdeId, Instant ahora, Instant enCursoDesde, int limite) {
        return repository.buscarPendientesDeRecuperacion(desdeId, ahora, enCursoDesde, PageRequest.of(0, limite))
                .stream().map(mapper::toDomainModel).toList();
    }
}
