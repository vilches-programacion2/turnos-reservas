package reservas.procesoreserva.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            proceso.setStatus(e.getError() == HoldCatedraException.Error.RESULTADO_INDETERMINADO
                    ? "CREATE_UNKNOWN" : "CREATE_REJECTED");
            proceso.setUpdatedAt(disponibilidadClock.instant());
            procesos.save(proceso);
            log.warn("Creación de hold fallida: procesoLocalId={} codigo={}", proceso.getId(), e.getError());
            throw e;
        }

        proceso.setHoldId(hold.holdId());
        proceso.setReservationProcessId(hold.reservationProcessId());
        proceso.setExpiresAt(hold.expiresAt());
        proceso.setStatus("HELD");
        proceso.setUpdatedAt(disponibilidadClock.instant());
        var guardado = procesos.save(proceso);
        log.info("Hold creado: procesoLocalId={} reservationProcessId={}", guardado.getId(), guardado.getReservationProcessId());
        return guardado;
    }
}
