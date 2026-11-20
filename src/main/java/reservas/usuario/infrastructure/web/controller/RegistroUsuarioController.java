package reservas.usuario.infrastructure.web.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reservas.usuario.application.exception.EmailYaRegistradoException;
import reservas.usuario.application.exception.LoginYaRegistradoException;
import reservas.usuario.application.service.UsuarioService;
import reservas.usuario.infrastructure.web.dto.RegistroUsuarioRequest;
import reservas.usuario.infrastructure.web.dto.UsuarioResponse;
import reservas.usuario.infrastructure.web.mapper.UsuarioDtoMapper;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
public class RegistroUsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioDtoMapper mapper;

    @PostMapping
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        var usuario = usuarioService.registrar(mapper.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(usuario));
    }

    @ExceptionHandler({LoginYaRegistradoException.class, EmailYaRegistradoException.class})
    public ProblemDetail duplicado(RuntimeException exception) {
        String code = exception instanceof LoginYaRegistradoException
                ? "USERNAME_ALREADY_EXISTS" : "EMAIL_ALREADY_EXISTS";
        return problema(code, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problema("VALIDATION_ERROR", "Los datos de registro no son válidos.");
        problem.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoInvalido() {
        return problema("INVALID_REQUEST", "Se requiere un cuerpo JSON válido.");
    }

    private ProblemDetail problema(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
