package reservas.procesoreserva.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reservas.procesoreserva.domain.model.AccionReserva;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JdbcAccionesReservaRepository implements AccionesReservaRepository {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    @Override
    @Transactional
    public boolean registrar(AccionReserva accion) {
        return jdbc.sql("""
                INSERT INTO reservation_action
                    (event_id, event_type, reservation_process_id, hold_id, occurred_at, expires_at,
                     kafka_partition, kafka_offset, details)
                VALUES (:event, :type, :process, :hold, :occurred, :expires, :partition, :position, CAST(:details AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("event", accion.eventId()).param("type", accion.eventType())
                .param("process", accion.reservationProcessId()).param("hold", accion.holdId())
                .param("occurred", fecha(accion.occurredAt())).param("expires", fecha(accion.expiresAt()))
                .param("partition", accion.partition()).param("position", accion.offset())
                .param("details", json.writeValueAsString(accion.details())).update() == 1;
    }

    @Override
    public Optional<PedidoTelefono> buscarPedido(String reservationProcessId, String holdId) {
        return jdbc.sql("""
                SELECT event_id, expires_at, submission_state
                FROM reservation_action
                WHERE reservation_process_id = :process AND hold_id = :hold
                  AND event_type = 'AdditionalInformationRequested'
                ORDER BY occurred_at DESC, kafka_offset DESC
                LIMIT 1
                """)
                .param("process", reservationProcessId).param("hold", holdId)
                .query((rs, row) -> new PedidoTelefono(rs.getString("event_id"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getString("submission_state"))).optional();
    }

    @Override
    @Transactional
    public boolean iniciarEnvio(String requestEventId, UUID eventId, Instant ahora) {
        return jdbc.sql("""
                UPDATE reservation_action
                SET submission_event_id = :id, submission_state = 'OUTCOME_UNKNOWN'
                WHERE event_id = :request AND submission_event_id IS NULL AND expires_at > :now
                """).param("id", eventId).param("request", requestEventId).param("now", fecha(ahora))
                .update() == 1;
    }

    @Override
    @Transactional
    public void terminarEnvio(String requestEventId, UUID eventId, boolean publicado) {
        jdbc.sql("""
                UPDATE reservation_action
                SET submission_event_id = :id, submission_state = :state
                WHERE event_id = :request AND submission_event_id = :current
                """).param("id", publicado ? eventId : null).param("state", publicado ? "PUBLISHED" : null)
                .param("request", requestEventId).param("current", eventId).update();
    }

    private OffsetDateTime fecha(Instant valor) {
        return valor == null ? null : valor.atOffset(ZoneOffset.UTC);
    }
}
