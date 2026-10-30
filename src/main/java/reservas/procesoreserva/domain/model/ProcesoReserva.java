package reservas.procesoreserva.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcesoReserva {
    private Long id;
    private Long userId;
    private String holdId;
    private String reservationProcessId;
    private Long reservationId;
    private Long professionalId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Instant expiresAt;
    private String professionalFirstName;
    private String professionalLastName;
    private String cancellationReason;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
