package reservas.usuario.application.usecases;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reservas.usuario.application.exception.CredencialesInvalidasException;
import reservas.usuario.domain.ports.in.AutenticarUsuarioUseCase;
import reservas.usuario.domain.ports.out.EmisorToken;
import reservas.usuario.domain.ports.out.PasswordHasher;
import reservas.usuario.domain.ports.out.UsuarioRepository;

@Component
@Slf4j
@RequiredArgsConstructor
public class AutenticarUsuarioUseCaseImpl implements AutenticarUsuarioUseCase {
    private final UsuarioRepository usuarios;
    private final PasswordHasher passwordHasher;
    private final EmisorToken emisorToken;

    @Override
    public String autenticar(String login, String password) {
        var usuario = usuarios.findByLoginIgnoreCase(login)
                .orElseThrow(CredencialesInvalidasException::new);
        if (!usuario.isActivated() || !passwordHasher.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }
        String token = emisorToken.emitir(usuario);
        log.info("Autenticación correcta: usuarioId={}", usuario.getId());
        return token;
    }
}
