package reservas.procesoreserva.domain.model;

import java.time.Instant;
import java.util.Map;

public record AccionReserva(String eventId, String eventType, String reservationProcessId,
        String holdId, Instant occurredAt, Instant expiresAt, int partition, long offset,
        Map<String, Object> details) {
}
