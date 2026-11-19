package reservas.procesoreserva.application.usecases;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.exception.ReservaPropiaException.Error;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.model.ReservaPropia;
import reservas.procesoreserva.domain.ports.in.ListarReservasPropiasUseCase;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient.Reserva;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListarReservasPropiasUseCaseImpl implements ListarReservasPropiasUseCase {
    private static final Set<String> ESTADOS = Set.of("PHONE_PENDING", "CONFIRMED", "CANCELLED", "FAILED");

    private final ProcesoReservaRepository procesos;
    private final ReservasCatedraClient catedra;

    @Override
    public List<ReservaPropia> listar(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ReservaPropiaException(Error.SOLICITUD_INVALIDA);

        String paciente = "user-" + userId;
        var reservas = catedra.listar(paciente, page, size);
        if (reservas.size() > size) throw paginaInconsistente(page);
        if (reservas.isEmpty()) return List.of();

        var ids = new HashSet<String>();
        for (var reserva : reservas) {
            if (!entradaValida(reserva, paciente) || !ids.add(reserva.reservationProcessId())) {
                throw paginaInconsistente(page);
            }
        }
        var propios = procesos.findByUserIdAndReservationProcessIdIn(userId, ids).stream()
                .collect(Collectors.toMap(ProcesoReserva::getReservationProcessId, Function.identity()));
        if (propios.size() != ids.size()) throw paginaInconsistente(page);

        for (var reserva : reservas) {
            if (!coincide(reserva, propios.get(reserva.reservationProcessId()))) throw paginaInconsistente(page);
        }
        return reservas.stream().map(reserva -> new ReservaPropia(propios.get(reserva.reservationProcessId()).getId(),
                reserva.reservationId(), reserva.professionalId(), reserva.professionalFirstName(),
                reserva.professionalLastName(), reserva.appointmentDate(), reserva.startTime(), reserva.endTime(),
                reserva.status(), reserva.confirmedAt(), reserva.cancelledAt())).toList();
    }

    private boolean entradaValida(Reserva reserva, String paciente) {
        return reserva != null && paciente.equals(reserva.externalPatientId()) && reserva.reservationProcessId() != null
                && !reserva.reservationProcessId().isBlank() && reserva.reservationProcessId().length() <= 255
                && reserva.reservationId() != null && reserva.reservationId() > 0 && reserva.status() != null
                && ESTADOS.contains(reserva.status());
    }

    private boolean coincide(Reserva reserva, ProcesoReserva propio) {
        return propio != null && propio.getProfessionalId().equals(reserva.professionalId())
                && propio.getAppointmentDate().equals(reserva.appointmentDate())
                && propio.getStartTime().equals(reserva.startTime())
                && (propio.getReservationId() == null || propio.getReservationId().equals(reserva.reservationId()));
    }

    private ReservaPropiaException paginaInconsistente(int page) {
        log.warn("Cátedra devolvió una página de reservas no verificable: page={}", page);
        return new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
    }
}
