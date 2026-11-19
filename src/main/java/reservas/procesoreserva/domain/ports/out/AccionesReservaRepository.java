package reservas.procesoreserva.domain.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import reservas.procesoreserva.domain.model.AccionReserva;

public interface AccionesReservaRepository {
    boolean registrar(AccionReserva accion);
    Optional<PedidoTelefono> buscarPedido(String reservationProcessId, String holdId);
    boolean iniciarEnvio(String requestEventId, UUID eventId, Instant ahora);
    void terminarEnvio(String requestEventId, UUID eventId, boolean publicado);

    record PedidoTelefono(String eventId, Instant expiresAt, String submissionState) {}
}
