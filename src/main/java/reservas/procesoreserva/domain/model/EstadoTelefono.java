package reservas.procesoreserva.domain.model;

import java.time.Instant;

public record EstadoTelefono(Long id, Estado status, Instant expiresAt) {
    public enum Estado { WAITING_FOR_REQUEST, PHONE_REQUIRED, PUBLISHED, OUTCOME_UNKNOWN, PHONE_WINDOW_CLOSED }
}
