package reservas.usuario.domain.ports.in;

import reservas.usuario.domain.model.RegistroUsuario;
import reservas.usuario.domain.model.Usuario;

public interface RegistrarUsuarioUseCase {
    Usuario registrar(RegistroUsuario registro);
}