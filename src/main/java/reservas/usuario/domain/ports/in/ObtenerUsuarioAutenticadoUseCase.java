package reservas.usuario.domain.ports.in;

import java.util.Optional;
import reservas.usuario.domain.model.Usuario;

public interface ObtenerUsuarioAutenticadoUseCase {
    Optional<Usuario> obtener(Long usuarioId);
}
