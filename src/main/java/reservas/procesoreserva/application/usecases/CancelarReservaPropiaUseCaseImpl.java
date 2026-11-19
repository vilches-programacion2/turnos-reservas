package reservas.procesoreserva.application.usecases;

import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.exception.ReservaPropiaException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.CancelarReservaPropiaUseCase;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient.Cancelacion;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelarReservaPropiaUseCaseImpl implements CancelarReservaPropiaUseCase {
    private final ProcesoReservaRepository procesos;
    private final ReservasCatedraClient catedra;
    private final Clock disponibilidadClock;

    @Override
    public ProcesoReserva cancelar(Long userId, Long id, String reason) {
        if (reason != null && reason.length() > 500) throw new ReservaPropiaException(Error.SOLICITUD_INVALIDA);
        var proceso = buscar(userId, id);
        switch (proceso.getStatus()) {
            case "CANCELLED" -> { return proceso; }
            case "CANCELLING" -> throw new ReservaPropiaException(Error.CANCELACION_EN_CURSO);
            case "CANCEL_UNKNOWN" -> throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
            case "CONFIRMED" -> { }
            default -> throw new ReservaPropiaException(Error.NO_CANCELABLE);
        }
        if (proceso.getReservationProcessId() == null || proceso.getReservationId() == null) {
            throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
        }

        proceso.setStatus("CANCELLING");
        guardar(proceso);
        Cancelacion resultado;
        try {
            resultado = catedra.cancelar(proceso.getReservationProcessId(), reason);
            if (resultado == null || !proceso.getReservationProcessId().equals(resultado.reservationProcessId())
                    || !Objects.equals(proceso.getReservationId(), resultado.reservationId()) || resultado.cancelledAt() == null) {
                throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
            }
        } catch (ReservaPropiaException e) {
            var actual = finalizar(userId, id, null, reason, e.getError() == Error.CANCELACION_INDETERMINADA);
            if ("CANCELLED".equals(actual.getStatus())) return actual;
            log.warn("Cancelación de reserva fallida reservationProcessId={} codigo={}",
                    proceso.getReservationProcessId(), e.getError());
            throw e;
        }
        var actual = finalizar(userId, id, resultado, reason, false);
        log.info("Cancelación de reserva procesada reservationProcessId={} estado={}",
                actual.getReservationProcessId(), actual.getStatus());
        return actual;
    }

    private ProcesoReserva finalizar(Long userId, Long id, Cancelacion resultado, String reason, boolean incierto) {
        for (int intento = 1; ; intento++) {
            var actual = buscar(userId, id);
            if (!"CANCELLING".equals(actual.getStatus())) return actual;
            if (resultado != null) {
                actual.registrarCancelacion(resultado.reservationId(), resultado.cancelledAt(), reason);
            } else {
                actual.setStatus(incierto ? "CANCEL_UNKNOWN" : "CONFIRMED");
            }
            try {
                var guardado = guardar(actual);
                // La primera respuesta y las repeticiones usan la fecha persistida.
                return resultado == null ? guardado : buscar(userId, id);
            } catch (OptimisticLockingFailureException e) {
                if (intento == 3) throw e;
            }
        }
    }

    private ProcesoReserva buscar(Long userId, Long id) {
        return procesos.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ReservaPropiaException(Error.NO_ENCONTRADA));
    }

    private ProcesoReserva guardar(ProcesoReserva proceso) {
        proceso.setUpdatedAt(disponibilidadClock.instant());
        return procesos.save(proceso);
    }
}
