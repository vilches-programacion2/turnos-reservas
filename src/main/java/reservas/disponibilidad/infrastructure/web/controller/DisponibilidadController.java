package reservas.disponibilidad.infrastructure.web.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException;
import reservas.disponibilidad.application.exception.SolicitudDisponibilidadInvalidaException;
import reservas.disponibilidad.application.service.DisponibilidadService;
import reservas.disponibilidad.infrastructure.web.dto.DisponibilidadResponse;
import reservas.disponibilidad.infrastructure.web.mapper.DisponibilidadDtoMapper;

@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
public class DisponibilidadController {
    private final DisponibilidadService disponibilidad;
    private final DisponibilidadDtoMapper mapper;

    @GetMapping("/{id}/availability")
    public ResponseEntity<DisponibilidadResponse> consultar(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapper.toResponse(disponibilidad.calcular(id, date)));
    }

    @ExceptionHandler({SolicitudDisponibilidadInvalidaException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ProblemDetail solicitudInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Se requiere un ID positivo y una fecha válida no pasada en Argentina.");
    }

    @ExceptionHandler(ConsultaCatalogoException.class)
    public ProblemDetail errorCatalogo(ConsultaCatalogoException exception) {
        return switch (exception.getError()) {
            case PROFESIONAL_NO_ENCONTRADO -> problema(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", "El profesional no existe.");
            case PROFESIONAL_DESHABILITADO -> problema(HttpStatus.CONFLICT, "PROFESSIONAL_DISABLED", "El profesional está deshabilitado.");
            case NO_INICIALIZADO -> problema(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_NOT_READY", "El catálogo aún no está inicializado.");
            case AUTENTICACION_REQUERIDA -> problema(HttpStatus.UNAUTHORIZED, "CATALOG_AUTHENTICATION_REQUIRED", "Se requiere un nuevo login compatible con Catálogo.");
            case ACCESO_DENEGADO -> problema(HttpStatus.FORBIDDEN, "CATALOG_ACCESS_DENIED", "No tiene permisos para consultar Catálogo.");
            case RESPUESTA_INVALIDA, ERROR_CONTRATO -> problema(HttpStatus.BAD_GATEWAY, "CATALOG_INVALID_RESPONSE", "La respuesta de Catálogo no es válida.");
            default -> problema(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "Catálogo no está disponible.");
        };
    }

    @ExceptionHandler(ConsultaOcupacionesException.class)
    public ProblemDetail errorOcupaciones(ConsultaOcupacionesException exception) {
        return switch (exception.getError()) {
            case RESPUESTA_INVALIDA, ERROR_CONTRATO -> problema(HttpStatus.BAD_GATEWAY, "OCCUPANCIES_INVALID_RESPONSE", "La respuesta de ocupaciones no es válida.");
            default -> problema(HttpStatus.SERVICE_UNAVAILABLE, "OCCUPANCIES_UNAVAILABLE", "No se pudieron consultar las ocupaciones centrales.");
        };
    }

    private ProblemDetail problema(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
