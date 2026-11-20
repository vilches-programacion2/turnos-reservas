package reservas.procesoreserva.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservaPropia(Long id, Long reservationId, Long professionalId, String professionalFirstName,
        String professionalLastName, LocalDate date, LocalTime startTime, LocalTime endTime, String status,
        Instant confirmedAt, Instant cancelledAt) {
}
