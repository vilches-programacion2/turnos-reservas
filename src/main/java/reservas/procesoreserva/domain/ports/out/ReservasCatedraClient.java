package reservas.procesoreserva.domain.ports.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservasCatedraClient {
    List<Reserva> listar(String externalPatientId, int page, int size);
    List<Reserva> listarEnFecha(String externalPatientId, LocalDate date, int page, int size);
    Cancelacion cancelar(String reservationProcessId, String reason);

    record Reserva(Long reservationId, String reservationProcessId, String externalPatientId, Long professionalId,
            String professionalFirstName, String professionalLastName, LocalDate appointmentDate, LocalTime startTime,
            LocalTime endTime, String status, Instant confirmedAt, Instant cancelledAt) {}

    record Cancelacion(Long reservationId, String reservationProcessId, Instant cancelledAt) {}
}
