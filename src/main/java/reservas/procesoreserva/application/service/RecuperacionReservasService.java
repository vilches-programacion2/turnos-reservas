package reservas.procesoreserva.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.exception.ReservaPropiaException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient.Reserva;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacionReservasService {
    private static final int PROCESOS_POR_TANDA = 10;
    private static final int TAMANIO_PAGINA = 20;
    private static final int LIMITE_PAGINAS = 3;
    private static final Set<String> CANCELACION_PENDIENTE = Set.of("CANCELLING", "CANCEL_UNKNOWN");

    private final ProcesoReservaRepository procesos;
    private final ReservasCatedraClient catedra;
    private final Clock disponibilidadClock;
    private long cursor;

    public synchronized void ejecutar(boolean catedraHabilitada, Duration esperaOperacion) {
        var ahora = disponibilidadClock.instant();
        var enCursoDesde = ahora.minus(esperaOperacion);
        var pendientes = procesos.buscarPendientesDeRecuperacion(cursor, ahora, enCursoDesde, PROCESOS_POR_TANDA);
        if (pendientes.isEmpty() && cursor > 0) {
            cursor = 0;
            pendientes = procesos.buscarPendientesDeRecuperacion(cursor, ahora, enCursoDesde, PROCESOS_POR_TANDA);
        }
        boolean consultarCatedra = catedraHabilitada;
        for (var proceso : pendientes) {
            cursor = proceso.getId();
            try {
                recuperar(proceso, consultarCatedra, ahora);
            } catch (ReservaPropiaException e) {
                log.warn("Recuperación de reserva fallida procesoLocalId={} reservationProcessId={} codigo={}",
                        proceso.getId(), proceso.getReservationProcessId(), e.getError());
                if (e.getError() == Error.CATEDRA_NO_DISPONIBLE) consultarCatedra = false;
            } catch (OptimisticLockingFailureException e) {
                log.debug("Recuperación omitida por cambio concurrente procesoLocalId={}", proceso.getId());
            }
        }
    }

    private void recuperar(ProcesoReserva proceso, boolean consultarCatedra, Instant ahora) {
        String anterior = proceso.getStatus();
        switch (anterior) {
            case "HELD" -> proceso.setStatus("EXPIRED");
            case "CREATING" -> proceso.setStatus("CREATE_UNKNOWN");
            default -> {
                if (consultarCatedra && proceso.getReservationProcessId() != null) {
                    buscarReserva(proceso).ifPresent(reserva -> aplicar(proceso, reserva));
                }
                switch (proceso.getStatus()) {
                    case "CONFIRMING" -> proceso.setStatus("CONFIRM_UNKNOWN");
                    case "CANCELLING" -> proceso.setStatus("CANCEL_UNKNOWN");
                    default -> { }
                }
            }
        }
        if (anterior.equals(proceso.getStatus())) return;

        proceso.setUpdatedAt(ahora);
        procesos.save(proceso);
        log.info("Reserva recuperada procesoLocalId={} reservationProcessId={} estado={}",
                proceso.getId(), proceso.getReservationProcessId(), proceso.getStatus());
    }

    private Optional<Reserva> buscarReserva(ProcesoReserva proceso) {
        String paciente = "user-" + proceso.getUserId();
        for (int pagina = 0; pagina < LIMITE_PAGINAS; pagina++) {
            var reservas = catedra.listarEnFecha(paciente, proceso.getAppointmentDate(), pagina, TAMANIO_PAGINA);
            if (reservas == null || reservas.size() > TAMANIO_PAGINA) {
                throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
            }
            Reserva coincidencia = null;
            for (var reserva : reservas) {
                if (reserva == null || !paciente.equals(reserva.externalPatientId())) {
                    throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                }
                if (proceso.getReservationProcessId().equals(reserva.reservationProcessId())) {
                    if (coincidencia != null || !coherente(proceso, reserva)) {
                        throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                    }
                    coincidencia = reserva;
                }
            }
            if (coincidencia != null) return Optional.of(coincidencia);
            if (reservas.size() < TAMANIO_PAGINA) return Optional.empty();
        }
        log.warn("Recuperación alcanzó el límite de páginas procesoLocalId={} reservationProcessId={}",
                proceso.getId(), proceso.getReservationProcessId());
        return Optional.empty();
    }

    private boolean coherente(ProcesoReserva proceso, Reserva reserva) {
        return reserva.reservationId() != null && reserva.reservationId() > 0
                && proceso.getProfessionalId().equals(reserva.professionalId())
                && proceso.getAppointmentDate().equals(reserva.appointmentDate())
                && proceso.getStartTime().equals(reserva.startTime())
                && (proceso.getReservationId() == null || proceso.getReservationId().equals(reserva.reservationId()));
    }

    private void aplicar(ProcesoReserva proceso, Reserva reserva) {
        boolean cancelacionPendiente = CANCELACION_PENDIENTE.contains(proceso.getStatus());
        if (reserva.status() == null) throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
        switch (reserva.status()) {
            case "CANCELLED" -> {
                if (reserva.cancelledAt() == null) throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                if (proceso.getConfirmedAt() == null) proceso.setConfirmedAt(reserva.confirmedAt());
                proceso.registrarCancelacion(reserva.reservationId(), reserva.cancelledAt(), null);
            }
            case "CONFIRMED" -> {
                if (reserva.confirmedAt() == null) throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                if (cancelacionPendiente) return;
                proceso.setReservationId(reserva.reservationId());
                proceso.setConfirmedAt(reserva.confirmedAt());
                proceso.setStatus("CONFIRMED");
            }
            case "FAILED" -> {
                if (cancelacionPendiente) return;
                proceso.setReservationId(reserva.reservationId());
                proceso.setStatus("FAILED");
            }
            case "PHONE_PENDING" -> {
                if ("CONFIRMING".equals(proceso.getStatus()) || "CONFIRM_UNKNOWN".equals(proceso.getStatus())) {
                    proceso.setStatus("WAITING_FOR_PHONE");
                }
            }
            default -> throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
        }
    }
}
