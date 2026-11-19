package reservas.procesoreserva.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.service.ReservasPropiasService;
import reservas.procesoreserva.domain.model.ReservaPropia;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class ReservasPropiasController {
    private final ReservasPropiasService reservas;

    @GetMapping
    public ResponseEntity<ReservasResponse> listar(Principal principal, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ReservasResponse(page, size, reservas.listar(Long.valueOf(principal.getName()), page, size)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelacionResponse> cancelar(Principal principal, @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelacionRequest request) {
        var resultado = reservas.cancelar(Long.valueOf(principal.getName()), id, request == null ? null : request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CancelacionResponse(resultado.getId(), resultado.getReservationId(),
                        resultado.getStatus(), resultado.getCancelledAt()));
    }

    @ExceptionHandler(ReservaPropiaException.class)
    public ProblemDetail error(ReservaPropiaException exception) {
        return switch (exception.getError()) {
            case SOLICITUD_INVALIDA -> entradaInvalida();
            case NO_ENCONTRADA -> problema(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "No se encontró la reserva.");
            case NO_CANCELABLE -> problema(HttpStatus.CONFLICT, "APPOINTMENT_NOT_CANCELLABLE", "Solo se puede cancelar una reserva confirmada.");
            case CANCELACION_EN_CURSO -> problema(HttpStatus.CONFLICT, "CANCELLATION_IN_PROGRESS", "La cancelación está en curso.");
            case CANCELACION_INDETERMINADA -> problema(HttpStatus.SERVICE_UNAVAILABLE, "CANCELLATION_OUTCOME_UNKNOWN", "No se pudo confirmar el resultado de la cancelación.");
            case CATEDRA_NO_DISPONIBLE -> problema(HttpStatus.SERVICE_UNAVAILABLE, "APPOINTMENT_INTEGRATION_UNAVAILABLE", "Cátedra no está disponible.");
            case CATEDRA_INCONSISTENTE -> problema(HttpStatus.BAD_GATEWAY, "APPOINTMENT_INTEGRATION_INCONSISTENT", "No se pudo verificar la respuesta de cátedra.");
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail entradaInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Revisá la página, el tamaño o el motivo de cancelación.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflictoConcurrente() {
        return problema(HttpStatus.CONFLICT, "PROCESS_CHANGED", "Otra solicitud modificó la reserva.");
    }

    private ProblemDetail problema(HttpStatus status, String code, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setProperty("code", code);
        return problema;
    }

    public record ReservasResponse(int page, int size, List<ReservaPropia> items) {}
    public record CancelacionRequest(@Size(max = 500) String reason) {}
    public record CancelacionResponse(Long id, Long reservationId, String status, Instant cancelledAt) {}
}
