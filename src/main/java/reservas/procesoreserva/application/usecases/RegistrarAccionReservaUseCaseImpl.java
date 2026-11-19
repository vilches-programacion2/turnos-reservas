package reservas.procesoreserva.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.domain.model.AccionReserva;
import reservas.procesoreserva.domain.ports.in.RegistrarAccionReservaUseCase;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrarAccionReservaUseCaseImpl implements RegistrarAccionReservaUseCase {
    private final AccionesReservaRepository acciones;

    @Override
    public void registrar(AccionReserva accion) {
        if (acciones.registrar(accion)) {
            log.info("Acción de reserva registrada tipo={} reservationProcessId={} eventId={}",
                    accion.eventType(), accion.reservationProcessId(), accion.eventId());
        }
    }
}
