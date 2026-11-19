package reservas.procesoreserva.domain.ports.out;

import java.time.Instant;
import java.util.UUID;

public interface PublicarTelefonoPort {
    Resultado publicar(EnvioTelefono envio);

    enum Resultado { PUBLICADO, NO_INICIADO, INDETERMINADO }

    record EnvioTelefono(UUID eventId, String reservationProcessId, String holdId,
            String requestEventId, String phoneNumber, Instant occurredAt) {
        @Override
        public String toString() {
            return "EnvioTelefono[eventId=" + eventId + "]";
        }
    }
}
