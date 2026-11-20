package reservas.procesoreserva.domain.ports.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public interface HoldsCatedraClient {
    Hold crear(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId);

    Confirmacion confirmar(String holdId, String externalPatientId, String firstName, String lastName);

    record Hold(String holdId, String reservationProcessId, Instant expiresAt) {}

    record Confirmacion(String reservationProcessId, Instant expiresAt) {}
}
