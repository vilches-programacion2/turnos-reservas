package reservas.usuario.domain.ports.out;

import java.util.Optional;
import reservas.usuario.domain.model.Usuario;

public interface UsuarioRepository {

    Usuario save(Usuario value);

    Optional<Usuario> findById(Long id);

    Optional<Usuario> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    boolean existsByEmailIgnoreCase(String email);
}