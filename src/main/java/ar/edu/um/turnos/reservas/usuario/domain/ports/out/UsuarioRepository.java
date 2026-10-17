package ar.edu.um.turnos.reservas.usuario.domain.ports.out;

import ar.edu.um.turnos.reservas.usuario.domain.model.Usuario;
import java.util.Optional;

public interface UsuarioRepository {
    Usuario save(Usuario value);
    Optional<Usuario> findById(Long id);
    Optional<Usuario> findByLoginIgnoreCase(String login);
}
