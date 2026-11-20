package reservas.procesoreserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.dao.OptimisticLockingFailureException;
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.application.exception.ProcesoHoldException;
import reservas.procesoreserva.application.exception.ProcesoHoldException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.ConfirmarHoldUseCase;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.usuario.domain.ports.in.ObtenerUsuarioAutenticadoUseCase;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmarHoldUseCaseImpl implements ConfirmarHoldUseCase {
    private final ProcesoReservaRepository procesos;
    private final HoldsCatedraClient catedra;
    private final ObtenerUsuarioAutenticadoUseCase usuarios;
    private final Clock disponibilidadClock;

    @Override
    public ProcesoReserva confirmar(Long userId, Long id) {
        if (id == null || id <= 0) throw new ProcesoHoldException(Error.SOLICITUD_INVALIDA);
        var proceso = procesos.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProcesoHoldException(Error.HOLD_NO_ENCONTRADO));
        switch (proceso.getStatus()) {
            case "CONFIRMING" -> throw new ProcesoHoldException(Error.CONFIRMACION_EN_CURSO);
            case "CONFIRM_UNKNOWN", "CREATE_UNKNOWN", "CREATING" -> throw new ProcesoHoldException(Error.RESULTADO_INDETERMINADO);
            case "EXPIRED" -> throw new ProcesoHoldException(Error.HOLD_VENCIDO);
            case "CONFIRMED", "CANCELLED", "INVALID", "PHONE_REJECTED" -> { return proceso; }
            case "HELD", "WAITING_FOR_PHONE" -> { }
            default -> throw new ProcesoHoldException(Error.ESTADO_INVALIDO);
        }
        if (proceso.getExpiresAt() == null || !proceso.getExpiresAt().isAfter(disponibilidadClock.instant())) {
            throw new ProcesoHoldException(Error.HOLD_VENCIDO);
        }
        if ("WAITING_FOR_PHONE".equals(proceso.getStatus())) return proceso;

        var usuario = usuarios.obtener(userId).orElseThrow(() -> new ProcesoHoldException(Error.USUARIO_NO_DISPONIBLE));
        String nombre = nombreValido(usuario.getFirstName());
        String apellido = nombreValido(usuario.getLastName());
        // El UPDATE versionado y su commit ganan el derecho a ejecutar el POST.
        proceso = guardarEstado(proceso, "CONFIRMING");
        Instant vence;
        try {
            var confirmacion = catedra.confirmar(proceso.getHoldId(), "user-" + usuario.getId(), nombre, apellido);
            if (!proceso.getReservationProcessId().equals(confirmacion.reservationProcessId())) {
                throw new HoldCatedraException(HoldCatedraException.Error.RESULTADO_INDETERMINADO);
            }
            vence = confirmacion.expiresAt();
        } catch (HoldCatedraException e) {
            String estado = switch (e.getError()) {
                case RESULTADO_INDETERMINADO, YA_CONFIRMADO -> "CONFIRM_UNKNOWN";
                case HOLD_VENCIDO, HOLD_NO_ENCONTRADO -> "EXPIRED";
                case CONFIGURACION_INVALIDA, CATEDRA_NO_AUTORIZADA -> "HELD";
                default -> "CONFIRM_REJECTED";
            };
            var actualizado = guardarResultado(userId, id, estado, null);
            if (!estado.equals(actualizado.getStatus())) return actualizado;
            log.warn("Confirmación de hold fallida: reservationProcessId={} codigo={}", proceso.getReservationProcessId(), e.getError());
            throw e;
        }
        var guardado = guardarResultado(userId, id, "WAITING_FOR_PHONE", vence);
        log.info("Confirmación inicial de hold procesada reservationProcessId={} estado={}",
                guardado.getReservationProcessId(), guardado.getStatus());
        return guardado;
    }

    private ProcesoReserva guardarResultado(Long userId, Long id, String estado, Instant vence) {
        for (int intento = 1; ; intento++) {
            var actual = procesos.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new ProcesoHoldException(Error.HOLD_NO_ENCONTRADO));
            if (!"CONFIRMING".equals(actual.getStatus()) && !"CONFIRM_UNKNOWN".equals(actual.getStatus())) return actual;
            if (vence != null) actual.setExpiresAt(vence);
            try {
                return guardarEstado(actual, estado);
            } catch (OptimisticLockingFailureException e) {
                if (intento == 3) throw e;
            }
        }
    }

    private ProcesoReserva guardarEstado(ProcesoReserva proceso, String estado) {
        proceso.setStatus(estado);
        proceso.setUpdatedAt(disponibilidadClock.instant());
        return procesos.save(proceso);
    }

    private String nombreValido(String value) {
        if (value == null || value.strip().length() < 2 || value.strip().length() > 100) {
            throw new ProcesoHoldException(Error.PERFIL_INCOMPLETO);
        }
        return value.strip();
    }
}
