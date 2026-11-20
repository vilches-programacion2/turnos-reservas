package reservas.procesoreserva.application.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reservas.procesoreserva.domain.model.AccionReserva;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultadosReservaService {
    private static final Set<String> ESPERANDO_RESULTADO =
            Set.of("CONFIRMING", "CONFIRM_UNKNOWN", "WAITING_FOR_PHONE", "PHONE_REJECTED");
    private static final int MAX_INTENTOS_LOCAL = 3;

    private final AccionesReservaRepository acciones;
    private final ProcesoReservaRepository procesos;
    private final TransactionTemplate transaccion;
    private final Clock disponibilidadClock;

    public void aplicar(String reservationProcessId) {
        for (int intento = 1; ; intento++) {
            try {
                transaccion.executeWithoutResult(status -> aplicarPendientes(reservationProcessId));
                return;
            } catch (OptimisticLockingFailureException e) {
                if (intento == MAX_INTENTOS_LOCAL) throw e;
            }
        }
    }

    public void recuperarGuardados() {
        var pendientes = acciones.buscarProcesosPendientes(100);
        while (!pendientes.isEmpty()) {
            pendientes.forEach(this::aplicar);
            pendientes = acciones.buscarProcesosPendientes(100);
        }
    }

    private void aplicarPendientes(String reservationProcessId) {
        var proceso = procesos.findByReservationProcessId(reservationProcessId).orElse(null);
        if (proceso == null || proceso.getHoldId() == null) return;

        var pendientes = acciones.bloquearResultadosPendientes(reservationProcessId, proceso.getHoldId());
        var avanzado = false;
        for (var accion : pendientes) {
            var aplicado = aplicarResultado(proceso, accion);
            acciones.marcarResultado(accion.eventId(), aplicado);
            avanzado |= aplicado;
        }
        if (avanzado) {
            proceso.setUpdatedAt(disponibilidadClock.instant());
            procesos.save(proceso);
            log.info("Resultados de reserva aplicados reservationProcessId={} estado={}",
                    reservationProcessId, proceso.getStatus());
        }
    }

    private boolean aplicarResultado(ProcesoReserva proceso, AccionReserva accion) {
        String estado = proceso.getStatus();
        boolean esperando = ESPERANDO_RESULTADO.contains(estado);
        return switch (accion.eventType()) {
            case "AppointmentConfirmed" -> esperando && confirmar(proceso, accion);
            case "AppointmentCancelled" -> (esperando || puedeRecibirCancelacion(estado)) && cancelar(proceso, accion);
            case "AdditionalInformationRejected" -> esperando && rechazarTelefono(proceso, accion);
            case "AppointmentProcessExpired" -> (esperando || "HELD".equals(estado)) && cerrar(proceso, accion, "EXPIRED");
            case "AppointmentProcessInvalid" -> esperando && cerrar(proceso, accion, "INVALID");
            default -> false;
        };
    }

    private boolean confirmar(ProcesoReserva proceso, AccionReserva accion) {
        if (!reservaCoincide(proceso, accion, "CONFIRMED") || !pedidoCoincide(accion)) return false;

        proceso.setReservationId(numero(accion, "reservationId"));
        proceso.setStatus("CONFIRMED");
        proceso.setConfirmedAt(accion.occurredAt());
        return true;
    }

    private boolean cancelar(ProcesoReserva proceso, AccionReserva accion) {
        if (!reservaCoincide(proceso, accion, "CANCELLED")) return false;

        proceso.registrarCancelacion(numero(accion, "reservationId"), accion.occurredAt(), texto(accion, "reason"));
        return true;
    }

    private boolean puedeRecibirCancelacion(String estado) {
        return switch (estado) {
            case "CONFIRMED", "CANCELLING", "CANCEL_UNKNOWN" -> true;
            default -> false;
        };
    }

    private boolean rechazarTelefono(ProcesoReserva proceso, AccionReserva accion) {
        if (!"INVALID_PHONE_NUMBER".equals(texto(accion, "reason")) || accion.expiresAt() == null
                || !pedidoCoincide(accion)
                || !acciones.reabrirPedido(texto(accion, "requestEventId"), accion.occurredAt(), accion.expiresAt())) {
            return false;
        }
        proceso.setStatus("PHONE_REJECTED");
        proceso.setExpiresAt(accion.expiresAt());
        return true;
    }

    private boolean cerrar(ProcesoReserva proceso, AccionReserva accion, String estado) {
        if (texto(accion, "reason") == null) return false;
        proceso.setStatus(estado);
        return true;
    }

    private boolean pedidoCoincide(AccionReserva accion) {
        var request = texto(accion, "requestEventId");
        return request != null && acciones.correspondeAlPedido(request, accion.reservationProcessId(), accion.holdId());
    }

    private boolean reservaCoincide(ProcesoReserva proceso, AccionReserva accion, String estado) {
        Long reservationId = numero(accion, "reservationId");
        if (reservationId == null || reservationId <= 0 || !estado.equals(texto(accion, "status"))
                || !proceso.getProfessionalId().equals(numero(accion, "professionalId"))
                || (proceso.getReservationId() != null && !proceso.getReservationId().equals(reservationId))) return false;
        try {
            var fecha = texto(accion, "date");
            var hora = texto(accion, "startTime");
            return fecha != null && hora != null && proceso.getAppointmentDate().equals(LocalDate.parse(fecha))
                    && proceso.getStartTime().equals(LocalTime.parse(hora));
        } catch (DateTimeException e) {
            return false;
        }
    }

    private String texto(AccionReserva accion, String campo) {
        return accion.details().get(campo) instanceof String texto && !texto.isBlank() ? texto : null;
    }

    private Long numero(AccionReserva accion, String campo) {
        return switch (accion.details().get(campo)) {
            case Long numero -> numero;
            case Integer numero -> numero.longValue();
            case null, default -> null;
        };
    }
}
