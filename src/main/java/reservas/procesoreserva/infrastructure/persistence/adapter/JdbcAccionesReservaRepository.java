package reservas.procesoreserva.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reservas.procesoreserva.domain.model.AccionReserva;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

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
                SET submission_event_id = :id, submission_state = 'OUTCOME_UNKNOWN', submission_started_at = :now
                WHERE event_id = :request AND submission_event_id IS NULL AND expires_at > :now
                  AND EXISTS (
                      SELECT 1 FROM reservation_process p
                      WHERE p.reservation_process_id = reservation_action.reservation_process_id
                        AND p.hold_id = reservation_action.hold_id
                        AND p.status IN ('CONFIRMING', 'CONFIRM_UNKNOWN', 'WAITING_FOR_PHONE', 'PHONE_REJECTED')
                  )
                """).param("id", eventId).param("request", requestEventId).param("now", fecha(ahora))
                .update() == 1;
    }

    @Override
    @Transactional
    public void terminarEnvio(String requestEventId, UUID eventId, boolean publicado) {
        jdbc.sql("""
                UPDATE reservation_action
                SET submission_event_id = :id, submission_state = :state,
                    submission_started_at = CASE WHEN :publicado THEN submission_started_at ELSE NULL END
                WHERE event_id = :request AND submission_event_id = :current
                """).param("id", publicado ? eventId : null).param("state", publicado ? "PUBLISHED" : null)
                .param("publicado", publicado).param("request", requestEventId).param("current", eventId).update();
    }

    @Override
    public List<String> buscarProcesosPendientes(int limite) {
        return jdbc.sql("""
                SELECT DISTINCT a.reservation_process_id
                FROM reservation_action a
                JOIN reservation_process p ON p.reservation_process_id = a.reservation_process_id AND p.hold_id = a.hold_id
                WHERE a.processing_status IS NULL AND a.event_type <> 'AdditionalInformationRequested'
                ORDER BY a.reservation_process_id
                LIMIT :limite
                """).param("limite", limite).query(String.class).list();
    }

    @Override
    public List<AccionReserva> bloquearResultadosPendientes(String reservationProcessId, String holdId) {
        return jdbc.sql("""
                SELECT event_id, event_type, occurred_at, expires_at, kafka_partition, kafka_offset, details
                FROM reservation_action
                WHERE reservation_process_id = :process AND hold_id = :hold
                  AND processing_status IS NULL AND event_type <> 'AdditionalInformationRequested'
                ORDER BY kafka_partition, kafka_offset, event_id
                FOR UPDATE
                """)
                .param("process", reservationProcessId).param("hold", holdId)
                .query((rs, row) -> {
                    var vence = rs.getObject("expires_at", OffsetDateTime.class);
                    var details = json.readValue(rs.getString("details"), new TypeReference<LinkedHashMap<String, Object>>() {});
                    return new AccionReserva(rs.getString("event_id"), rs.getString("event_type"),
                            reservationProcessId, holdId, rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                            vence == null ? null : vence.toInstant(), rs.getInt("kafka_partition"), rs.getLong("kafka_offset"), details);
                }).list();
    }

    @Override
    public void marcarResultado(String eventId, boolean aplicado) {
        jdbc.sql("UPDATE reservation_action SET processing_status = :status WHERE event_id = :event")
                .param("status", aplicado ? "APPLIED" : "IGNORED").param("event", eventId).update();
    }

    @Override
    public boolean correspondeAlPedido(String requestEventId, String reservationProcessId, String holdId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM reservation_action
                    WHERE event_id = :event AND reservation_process_id = :process AND hold_id = :hold
                      AND event_type = 'AdditionalInformationRequested'
                )
                """).param("event", requestEventId).param("process", reservationProcessId).param("hold", holdId)
                .query(Boolean.class).single();
    }

    @Override
    public boolean reabrirPedido(String requestEventId, Instant rechazadoEn, Instant vence) {
        return jdbc.sql("""
                UPDATE reservation_action
                SET submission_event_id = NULL, submission_state = NULL, submission_started_at = NULL,
                    expires_at = :vence
                WHERE event_id = :event AND submission_event_id IS NOT NULL
                  AND (submission_started_at IS NULL OR submission_started_at <= :rechazado)
                """).param("event", requestEventId).param("rechazado", fecha(rechazadoEn))
                .param("vence", fecha(vence)).update() == 1;
    }

    private OffsetDateTime fecha(Instant valor) {
        return valor == null ? null : valor.atOffset(ZoneOffset.UTC);
    }
}
