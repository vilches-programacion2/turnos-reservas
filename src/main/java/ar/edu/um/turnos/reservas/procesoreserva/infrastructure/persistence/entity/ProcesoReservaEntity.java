package ar.edu.um.turnos.reservas.procesoreserva.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reservation_process")
public class ProcesoReservaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "hold_id", nullable = true, length = 255)
    private String holdId;
    @Column(name = "reservation_process_id", nullable = true, length = 255)
    private String reservationProcessId;
    @Column(name = "reservation_id", nullable = true)
    private Long reservationId;
    @Column(name = "professional_id", nullable = false)
    private Long professionalId;
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = true)
    private LocalTime endTime;
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    @Column(name = "expires_at", nullable = true)
    private Instant expiresAt;
    @Column(name = "professional_first_name", nullable = true, length = 100)
    private String professionalFirstName;
    @Column(name = "professional_last_name", nullable = true, length = 100)
    private String professionalLastName;
    @Column(name = "cancellation_reason", nullable = true, length = 500)
    private String cancellationReason;
    @Column(name = "confirmed_at", nullable = true)
    private Instant confirmedAt;
    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelledAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
