package reservas.usuario.infrastructure.web.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reservas.usuario.application.exception.CredencialesInvalidasException;
import reservas.usuario.application.service.UsuarioService;
import reservas.usuario.infrastructure.web.dto.AutenticacionRequest;
import reservas.usuario.infrastructure.web.dto.AutenticacionResponse;
import reservas.usuario.infrastructure.web.dto.UsuarioResponse;
import reservas.usuario.infrastructure.web.mapper.UsuarioDtoMapper;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AutenticacionController {
    private final UsuarioService usuarios;
    private final UsuarioDtoMapper mapper;

    @PostMapping("/authenticate")
    public ResponseEntity<AutenticacionResponse> autenticar(@Valid @RequestBody AutenticacionRequest request) {
        String token = usuarios.autenticar(request.getUsername(), request.getPassword());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AutenticacionResponse.builder().idToken(token).build());
    }

    @GetMapping("/account")
    public UsuarioResponse cuenta(Principal principal) {
        return mapper.toResponse(usuarios.obtenerAutenticado(Long.valueOf(principal.getName())));
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ProblemDetail credencialesInvalidas() {
        return problema(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciales inválidas.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail entradaInvalida() {
        return problema(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Se requieren username y password válidos.");
    }

    private ProblemDetail problema(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
