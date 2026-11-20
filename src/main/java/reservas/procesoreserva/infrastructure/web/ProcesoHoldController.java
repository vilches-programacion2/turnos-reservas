package reservas.procesoreserva.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.application.exception.ProcesoHoldException;
import reservas.procesoreserva.application.service.ProcesoHoldService;
import reservas.procesoreserva.domain.model.ProcesoReserva;

@RestController
@RequestMapping("/api/appointment-holds")
@RequiredArgsConstructor
public class ProcesoHoldController {
    private final ProcesoHoldService holds;

    @PostMapping
    public ResponseEntity<HoldResponse> crear(Principal principal, @RequestBody @Valid CrearRequest request) {
        var proceso = holds.crear(Long.valueOf(principal.getName()), request.professionalId(), request.date(), request.startTime());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(respuesta(proceso));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<HoldResponse> confirmar(Principal principal, @PathVariable Long id) {
        var proceso = holds.confirmar(Long.valueOf(principal.getName()), id);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(respuesta(proceso));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstadoProcesoResponse> consultar(Principal principal, @PathVariable Long id) {
        var proceso = holds.consultar(Long.valueOf(principal.getName()), id);
        var estado = new EstadoProcesoResponse(proceso.getId(), proceso.getProfessionalId(),
                proceso.getAppointmentDate(), proceso.getStartTime(), proceso.getStatus(), proceso.getExpiresAt(),
                proceso.getReservationId(), proceso.getConfirmedAt(), proceso.getCancelledAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail entradaInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Revisá el profesional, la fecha y el horario.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail procesoConcurrente() {
        return problema(HttpStatus.CONFLICT, "PROCESS_CHANGED", "Otra solicitud modificó el proceso.");
    }

    @ExceptionHandler(ProcesoHoldException.class)
    public ProblemDetail errorProceso(ProcesoHoldException e) {
        return switch (e.getError()) {
            case SOLICITUD_INVALIDA -> entradaInvalida();
            case USUARIO_NO_DISPONIBLE -> problema(HttpStatus.FORBIDDEN, "USER_UNAVAILABLE", "La cuenta no está disponible.");
            case PERFIL_INCOMPLETO -> problema(HttpStatus.BAD_REQUEST, "PATIENT_PROFILE_INVALID", "El nombre y el apellido deben tener entre 2 y 100 caracteres.");
            case HOLD_NO_ENCONTRADO -> problema(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "No se encontró el bloqueo.");
            case HOLD_VENCIDO -> problema(HttpStatus.GONE, "HOLD_EXPIRED", "El bloqueo venció.");
            case CONFIRMACION_EN_CURSO -> problema(HttpStatus.CONFLICT, "CONFIRMATION_IN_PROGRESS", "La confirmación está en curso.");
            case RESULTADO_INDETERMINADO -> resultadoIndeterminado();
            case ESTADO_INVALIDO -> problema(HttpStatus.CONFLICT, "INVALID_HOLD_STATE", "El bloqueo no admite confirmación en su estado actual.");
        };
    }

    @ExceptionHandler(HoldCatedraException.class)
    public ProblemDetail errorCatedra(HoldCatedraException e) {
        return switch (e.getError()) {
            case CONFIGURACION_INVALIDA, CATEDRA_NO_AUTORIZADA ->
                    problema(HttpStatus.SERVICE_UNAVAILABLE, "HOLD_INTEGRATION_UNAVAILABLE", "La integración de bloqueos no está disponible.");
            case PROFESIONAL_NO_ENCONTRADO -> problema(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", "El profesional no existe.");
            case PROFESIONAL_DESHABILITADO -> problema(HttpStatus.CONFLICT, "PROFESSIONAL_DISABLED", "El profesional está deshabilitado.");
            case SLOT_INVALIDO -> problema(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "El horario no corresponde a un turno habilitado.");
            case SLOT_OCUPADO -> problema(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "El horario ya no está disponible.");
            case HOLD_NO_ENCONTRADO -> problema(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "No se encontró el bloqueo.");
            case HOLD_VENCIDO -> problema(HttpStatus.GONE, "HOLD_EXPIRED", "El bloqueo venció.");
            case PROPIETARIO_INVALIDO -> problema(HttpStatus.BAD_GATEWAY, "HOLD_OWNERSHIP_MISMATCH", "Cátedra rechazó la asociación del bloqueo.");
            case YA_CONFIRMADO, RESULTADO_INDETERMINADO -> resultadoIndeterminado();
        };
    }

    private ProblemDetail resultadoIndeterminado() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "HOLD_OUTCOME_UNKNOWN", "No se pudo determinar el resultado de la operación.");
    }

    private ProblemDetail problema(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private HoldResponse respuesta(ProcesoReserva proceso) {
        return new HoldResponse(proceso.getId(), proceso.getProfessionalId(), proceso.getAppointmentDate(),
                proceso.getStartTime(), proceso.getStatus(), proceso.getExpiresAt());
    }

    public record CrearRequest(@NotNull @Positive Long professionalId, @NotNull LocalDate date,
            @NotNull LocalTime startTime) {}
    public record HoldResponse(Long id, Long professionalId, LocalDate date, LocalTime startTime, String status,
            Instant expiresAt) {}
    public record EstadoProcesoResponse(Long id, Long professionalId, LocalDate date, LocalTime startTime,
            String status, Instant expiresAt, Long reservationId, Instant confirmedAt, Instant cancelledAt) {}
}
