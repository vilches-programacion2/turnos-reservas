package reservas.procesoreserva.infrastructure.kafka;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.domain.model.AccionReserva;
import reservas.procesoreserva.domain.ports.in.RegistrarAccionReservaUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccionesReservaListener implements AcknowledgingMessageListener<String, String> {
    private static final String PEDIDO = "AdditionalInformationRequested";
    private static final Map<String, List<String>> CAMPOS = Map.of(
            PEDIDO, List.of("requiredField"),
            "AdditionalInformationRejected", List.of("groupId", "requestEventId", "reason"),
            "AppointmentConfirmed", List.of("groupId", "requestEventId", "reservationId", "professionalId", "date", "startTime", "status"),
            "AppointmentProcessExpired", List.of("groupId", "requestEventId", "reason"),
            "AppointmentProcessInvalid", List.of("groupId", "requestEventId", "reason"),
            "AppointmentCancelled", List.of("groupId", "requestEventId", "reservationId", "professionalId", "date", "startTime", "status", "reason"));

    private final JsonMapper json;
    private final RegistrarAccionReservaUseCase registrar;

    @Override
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        AccionReserva accion;
        try {
            accion = convertir(record);
        } catch (JacksonException | IllegalArgumentException | DateTimeParseException e) {
            log.warn("Acción de reserva inválida partición={} offset={}", record.partition(), record.offset());
            ack.acknowledge();
            return;
        }
        try {
            registrar.registrar(accion);
        } catch (RuntimeException e) {
            log.error("Acción de reserva sin persistir reservationProcessId={} eventId={} partición={} offset={} error={}",
                    accion.reservationProcessId(), accion.eventId(), record.partition(), record.offset(),
                    e.getClass().getSimpleName());
            throw new IllegalStateException("No se pudo persistir la acción de reserva");
        }
        ack.acknowledge();
    }

    private AccionReserva convertir(ConsumerRecord<String, String> record) {
        if (record.value() == null) throw new IllegalArgumentException();
        var nodo = json.readTree(record.value());
        String tipo = texto(nodo, "eventType");
        String proceso = texto(nodo, "reservationProcessId");
        var version = nodo.path("schemaVersion");
        if (!CAMPOS.containsKey(tipo) || !proceso.equals(record.key()) || !version.isIntegralNumber()
                || !version.canConvertToInt() || version.intValue() != 1) throw new IllegalArgumentException();
        var pedido = PEDIDO.equals(tipo);
        if (pedido && !"PHONE_NUMBER".equals(texto(nodo, "requiredField"))) throw new IllegalArgumentException();
        var vence = pedido || nodo.hasNonNull("expiresAt") ? Instant.parse(texto(nodo, "expiresAt")) : null;
        var datos = new LinkedHashMap<String, Object>();
        for (String campo : CAMPOS.get(tipo)) {
            var valor = nodo.path(campo);
            if (valor.isString()) datos.put(campo, valor.asString());
            if (valor.isIntegralNumber()) datos.put(campo, valor.numberValue());
        }
        return new AccionReserva(texto(nodo, "eventId"), tipo, proceso, texto(nodo, "holdId"),
                Instant.parse(texto(nodo, "occurredAt")), vence, record.partition(), record.offset(), datos);
    }

    private String texto(JsonNode nodo, String campo) {
        var valor = nodo.path(campo);
        if (!valor.isString() || valor.asString().isBlank() || valor.asString().length() > 255) {
            throw new IllegalArgumentException();
        }
        return valor.asString();
    }
}
