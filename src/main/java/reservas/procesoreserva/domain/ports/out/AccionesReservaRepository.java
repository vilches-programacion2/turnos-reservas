package reservas.procesoreserva.domain.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import reservas.procesoreserva.domain.model.AccionReserva;

public interface AccionesReservaRepository {
    boolean registrar(AccionReserva accion);
    Optional<PedidoTelefono> buscarPedido(String reservationProcessId, String holdId);
    boolean iniciarEnvio(String requestEventId, UUID eventId, Instant ahora);
    void terminarEnvio(String requestEventId, UUID eventId, boolean publicado);
    List<String> buscarProcesosPendientes(int limite);
    List<AccionReserva> bloquearResultadosPendientes(String reservationProcessId, String holdId);
    void marcarResultado(String eventId, boolean aplicado);
    boolean correspondeAlPedido(String requestEventId, String reservationProcessId, String holdId);
    boolean reabrirPedido(String requestEventId, Instant rechazadoEn, Instant vence);

    record PedidoTelefono(String eventId, Instant expiresAt, String submissionState) {}
}
