package reservas.procesoreserva.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reservas.procesoreserva.application.exception.TelefonoReservaException;
import reservas.procesoreserva.application.exception.TelefonoReservaException.Error;
import reservas.procesoreserva.domain.model.EstadoTelefono;
import reservas.procesoreserva.domain.model.EstadoTelefono.Estado;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.ports.in.ConsultarTelefonoUseCase;
import reservas.procesoreserva.domain.ports.in.EnviarTelefonoUseCase;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository;
import reservas.procesoreserva.domain.ports.out.AccionesReservaRepository.PedidoTelefono;
import reservas.procesoreserva.domain.ports.out.ProcesoReservaRepository;
import reservas.procesoreserva.domain.ports.out.PublicarTelefonoPort;
import reservas.procesoreserva.domain.ports.out.PublicarTelefonoPort.EnvioTelefono;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntercambioTelefonoUseCaseImpl implements ConsultarTelefonoUseCase, EnviarTelefonoUseCase {
    private static final Pattern FORMATO_TELEFONO = Pattern.compile("\\+?[0-9]{7,15}");

    private final ProcesoReservaRepository procesos;
    private final AccionesReservaRepository acciones;
    private final PublicarTelefonoPort kafka;
    private final Clock disponibilidadClock;

    @Override
    public EstadoTelefono consultar(Long userId, Long id) {
        var proceso = procesoPropio(userId, id);
        return estado(proceso, buscarPedido(proceso), disponibilidadClock.instant());
    }

    @Override
    public EstadoTelefono enviar(Long userId, Long id, String telefono) {
        var proceso = procesoPropio(userId, id);
        var pedido = buscarPedido(proceso);
        var ahora = disponibilidadClock.instant();
        var actual = estado(proceso, pedido, ahora);
        if (actual.status() != Estado.PHONE_REQUIRED) return actual;

        String numero = normalizar(telefono);
        var eventId = UUID.randomUUID();
        ahora = disponibilidadClock.instant();
        if (!acciones.iniciarEnvio(pedido.eventId(), eventId, ahora)) {
            return consultar(userId, id);
        }

        var resultado = kafka.publicar(new EnvioTelefono(eventId, proceso.getReservationProcessId(),
                proceso.getHoldId(), pedido.eventId(), numero, ahora));
        if (resultado != PublicarTelefonoPort.Resultado.INDETERMINADO) {
            acciones.terminarEnvio(pedido.eventId(), eventId, resultado == PublicarTelefonoPort.Resultado.PUBLICADO);
        }
        log.info("Envío de teléfono resultado={} reservationProcessId={} eventId={}",
                resultado, proceso.getReservationProcessId(), eventId);
        if (resultado == PublicarTelefonoPort.Resultado.NO_INICIADO) {
            throw new TelefonoReservaException(Error.KAFKA_NO_DISPONIBLE);
        }
        var estado = resultado == PublicarTelefonoPort.Resultado.PUBLICADO ? Estado.PUBLISHED : Estado.OUTCOME_UNKNOWN;
        return new EstadoTelefono(id, estado, pedido.expiresAt());
    }

    private ProcesoReserva procesoPropio(Long userId, Long id) {
        var proceso = procesos.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TelefonoReservaException(Error.PROCESO_NO_ENCONTRADO));
        switch (proceso.getStatus()) {
            case "CONFIRMING", "CONFIRM_UNKNOWN", "WAITING_FOR_PHONE", "PHONE_REJECTED",
                    "EXPIRED", "CONFIRMED", "CANCELLED", "INVALID" -> { }
            default -> throw new TelefonoReservaException(Error.ESTADO_INVALIDO);
        }
        return proceso;
    }

    private PedidoTelefono buscarPedido(ProcesoReserva proceso) {
        return acciones.buscarPedido(proceso.getReservationProcessId(), proceso.getHoldId()).orElse(null);
    }

    private EstadoTelefono estado(ProcesoReserva proceso, PedidoTelefono pedido, Instant ahora) {
        var fin = pedido == null ? proceso.getExpiresAt() : pedido.expiresAt();
        Estado estado;
        if (switch (proceso.getStatus()) {
            case "EXPIRED", "CONFIRMED", "CANCELLED", "INVALID" -> true;
            default -> fin != null && !fin.isAfter(ahora);
        }) {
            estado = Estado.PHONE_WINDOW_CLOSED;
        } else if (pedido == null) {
            estado = Estado.WAITING_FOR_REQUEST;
        } else {
            estado = pedido.submissionState() == null ? Estado.PHONE_REQUIRED : Estado.valueOf(pedido.submissionState());
        }
        return new EstadoTelefono(proceso.getId(), estado, fin);
    }

    private String normalizar(String telefono) {
        var numero = telefono == null || telefono.length() > 100 ? "" : telefono.replaceAll("[ () .-]", "");
        if (!FORMATO_TELEFONO.matcher(numero).matches()) {
            throw new TelefonoReservaException(Error.TELEFONO_INVALIDO);
        }
        return numero;
    }
}
