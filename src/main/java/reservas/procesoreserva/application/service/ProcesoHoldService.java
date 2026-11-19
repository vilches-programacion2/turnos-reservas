package reservas.procesoreserva.application.service;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.CrearHoldUseCase;
import reservas.procesoreserva.domain.ports.in.ConfirmarHoldUseCase;
import reservas.procesoreserva.domain.ports.in.ConsultarProcesoReservaUseCase;

@Service
@RequiredArgsConstructor
public class ProcesoHoldService {
    private final CrearHoldUseCase crearHold;
    private final ConfirmarHoldUseCase confirmarHold;
    private final ConsultarProcesoReservaUseCase consultarProceso;

    public ProcesoReserva crear(Long userId, Long professionalId, LocalDate date, LocalTime startTime) {
        return crearHold.crear(userId, professionalId, date, startTime);
    }

    public ProcesoReserva confirmar(Long userId, Long id) {
        return confirmarHold.confirmar(userId, id);
    }

    public ProcesoReserva consultar(Long userId, Long id) {
        return consultarProceso.consultar(userId, id);
    }
}
