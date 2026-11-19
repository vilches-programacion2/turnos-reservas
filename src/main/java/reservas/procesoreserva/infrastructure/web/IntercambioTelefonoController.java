package reservas.procesoreserva.infrastructure.web;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import reservas.procesoreserva.application.exception.TelefonoReservaException;
import reservas.procesoreserva.application.service.IntercambioTelefonoService;
import reservas.procesoreserva.domain.model.EstadoTelefono;

@RestController
@RequestMapping("/api/appointment-holds/{id}/phone")
@RequiredArgsConstructor
public class IntercambioTelefonoController {
    private final IntercambioTelefonoService telefono;

    @GetMapping
    public ResponseEntity<EstadoTelefono> consultar(Principal principal, @PathVariable Long id) {
        return respuesta(HttpStatus.OK, telefono.consultar(Long.valueOf(principal.getName()), id));
    }

    @PostMapping
    public ResponseEntity<EstadoTelefono> enviar(Principal principal, @PathVariable Long id, @RequestBody PhoneRequest request) {
        var estado = telefono.enviar(Long.valueOf(principal.getName()), id, request.phoneNumber());
        return switch (estado.status()) {
            case PUBLISHED -> respuesta(HttpStatus.ACCEPTED, estado);
            case OUTCOME_UNKNOWN -> respuesta(HttpStatus.SERVICE_UNAVAILABLE, estado);
            case PHONE_WINDOW_CLOSED -> respuesta(HttpStatus.GONE, estado);
            case WAITING_FOR_REQUEST, PHONE_REQUIRED -> respuesta(HttpStatus.CONFLICT, estado);
        };
    }

    @ExceptionHandler(TelefonoReservaException.class)
    public ProblemDetail error(TelefonoReservaException e) {
        return switch (e.getError()) {
            case PROCESO_NO_ENCONTRADO -> problema(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "No se encontró el bloqueo.");
            case ESTADO_INVALIDO -> problema(HttpStatus.CONFLICT, "INVALID_HOLD_STATE", "El bloqueo no admite el envío de teléfono.");
            case TELEFONO_INVALIDO -> solicitudInvalida();
            case KAFKA_NO_DISPONIBLE -> problema(HttpStatus.SERVICE_UNAVAILABLE, "PHONE_INTEGRATION_UNAVAILABLE", "La integración de teléfono no está disponible.");
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail solicitudInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", "Ingresá un número de teléfono válido.");
    }

    private ResponseEntity<EstadoTelefono> respuesta(HttpStatus status, EstadoTelefono estado) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(estado);
    }

    private ProblemDetail problema(HttpStatus status, String code, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setProperty("code", code);
        return problema;
    }

    public record PhoneRequest(String phoneNumber) {
        @Override
        public String toString() {
            return "PhoneRequest";
        }
    }
}
