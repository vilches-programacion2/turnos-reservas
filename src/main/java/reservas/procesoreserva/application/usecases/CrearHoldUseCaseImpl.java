package reservas.procesoreserva.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.application.exception.ProcesoHoldException;
import reservas.procesoreserva.application.exception.ProcesoHoldException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.CrearHoldUseCase;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.usuario.domain.ports.in.ObtenerUsuarioAutenticadoUseCase;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrearHoldUseCaseImpl implements CrearHoldUseCase {
    private final ProcesoReservaRepository procesos;
    private final HoldsCatedraClient catedra;
    private final ObtenerUsuarioAutenticadoUseCase usuarios;
    private final Clock disponibilidadClock;

    @Override
    public ProcesoReserva crear(Long userId, Long professionalId, LocalDate date, LocalTime startTime) {
        if (professionalId == null || professionalId <= 0 || date == null || startTime == null
                || !date.atTime(startTime).isAfter(LocalDateTime.now(disponibilidadClock))) {
            throw new ProcesoHoldException(Error.SOLICITUD_INVALIDA);
        }
        var usuario = usuarios.obtener(userId).orElseThrow(() -> new ProcesoHoldException(Error.USUARIO_NO_DISPONIBLE));
        var ahora = disponibilidadClock.instant();
        var proceso = procesos.save(ProcesoReserva.builder().userId(usuario.getId()).professionalId(professionalId)
                .appointmentDate(date).startTime(startTime).status("CREATING").createdAt(ahora).updatedAt(ahora).build());

        HoldsCatedraClient.Hold hold;
        try {
            hold = catedra.crear(professionalId, date, startTime, "user-" + usuario.getId());
        } catch (HoldCatedraException e) {
            guardarResultado(proceso, null, e.getError() == HoldCatedraException.Error.RESULTADO_INDETERMINADO
                    ? "CREATE_UNKNOWN" : "CREATE_REJECTED");
            log.warn("Creación de hold fallida: procesoLocalId={} codigo={}", proceso.getId(), e.getError());
            throw e;
        }

        var guardado = guardarResultado(proceso, hold, "HELD");
        log.info("Hold creado: procesoLocalId={} reservationProcessId={}", guardado.getId(), guardado.getReservationProcessId());
        return guardado;
    }

    private ProcesoReserva guardarResultado(ProcesoReserva proceso, HoldsCatedraClient.Hold hold, String estado) {
        for (int intento = 1; ; intento++) {
            if (hold != null) {
                proceso.setHoldId(hold.holdId());
                proceso.setReservationProcessId(hold.reservationProcessId());
                proceso.setExpiresAt(hold.expiresAt());
            }
            proceso.setStatus(estado);
            proceso.setUpdatedAt(disponibilidadClock.instant());
            try {
                return procesos.save(proceso);
            } catch (OptimisticLockingFailureException e) {
                if (intento == 3) throw e;
                proceso = procesos.findByIdAndUserId(proceso.getId(), proceso.getUserId())
                        .filter(actual -> "CREATE_UNKNOWN".equals(actual.getStatus())).orElseThrow(() -> e);
            }
        }
    }
}
