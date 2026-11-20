package reservas.procesoreserva.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.exception.ProcesoHoldException;
import reservas.procesoreserva.application.exception.ProcesoHoldException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.ConsultarProcesoReservaUseCase;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;

@Component
@RequiredArgsConstructor
public class ConsultarProcesoReservaUseCaseImpl implements ConsultarProcesoReservaUseCase {
    private final ProcesoReservaRepository procesos;

    @Override
    public ProcesoReserva consultar(Long userId, Long id) {
        return procesos.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProcesoHoldException(Error.HOLD_NO_ENCONTRADO));
    }
}
