package reservas.usuario.application.usecases;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.in.ObtenerUsuarioAutenticadoUseCase;
import reservas.usuario.domain.ports.out.UsuarioRepository;

@Component
@RequiredArgsConstructor
public class ObtenerUsuarioAutenticadoUseCaseImpl implements ObtenerUsuarioAutenticadoUseCase {
    private final UsuarioRepository usuarios;

    @Override
    public Optional<Usuario> obtener(Long usuarioId) {
        return usuarios.findById(usuarioId).filter(Usuario::isActivated);
    }
}
