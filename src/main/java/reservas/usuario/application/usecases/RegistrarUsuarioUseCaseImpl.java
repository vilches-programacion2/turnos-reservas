package reservas.usuario.application.usecases;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.usuario.application.exception.EmailYaRegistradoException;
import reservas.usuario.application.exception.LoginYaRegistradoException;
import reservas.usuario.domain.model.RegistroUsuario;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.in.RegistrarUsuarioUseCase;
import reservas.usuario.domain.ports.out.PasswordHasher;
import reservas.usuario.domain.ports.out.UsuarioRepository;

@Component
@RequiredArgsConstructor
public class RegistrarUsuarioUseCaseImpl implements RegistrarUsuarioUseCase {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public Usuario registrar(RegistroUsuario registro) {
        if (usuarioRepository.existsByLoginIgnoreCase(registro.getLogin())) {
            throw new LoginYaRegistradoException();
        }

        if (usuarioRepository.existsByEmailIgnoreCase(registro.getEmail())) {
            throw new EmailYaRegistradoException();
        }

        String passwordHash = passwordHasher.hash(registro.getPassword());
        Instant ahora = Instant.now();

        Usuario usuario = Usuario.builder()
                .login(registro.getLogin())
                .passwordHash(passwordHash)
                .firstName(registro.getFirstName())
                .lastName(registro.getLastName())
                .email(registro.getEmail())
                .imageUrl(registro.getImageUrl())
                .langKey(registro.getLangKey())
                .activated(true)
                .authorities(new HashSet<>(Set.of("ROLE_USER")))
                .createdAt(ahora)
                .updatedAt(ahora)
                .build();

        return usuarioRepository.save(usuario);
    }
}