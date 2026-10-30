package reservas.usuario.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reservas.usuario.application.exception.CredencialesInvalidasException;
import reservas.usuario.domain.model.RegistroUsuario;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.in.AutenticarUsuarioUseCase;
import reservas.usuario.domain.ports.in.ObtenerUsuarioAutenticadoUseCase;
import reservas.usuario.domain.ports.in.RegistrarUsuarioUseCase;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final RegistrarUsuarioUseCase registrarUsuarioUseCase;
    private final AutenticarUsuarioUseCase autenticarUsuarioUseCase;
    private final ObtenerUsuarioAutenticadoUseCase obtenerUsuarioAutenticadoUseCase;

    public String autenticar(String login, String password) {
        return autenticarUsuarioUseCase.autenticar(login, password);
    }

    public Usuario obtenerAutenticado(Long usuarioId) {
        return obtenerUsuarioAutenticadoUseCase.obtener(usuarioId)
                .orElseThrow(CredencialesInvalidasException::new);
    }

    public Usuario registrar(RegistroUsuario registro) {
        Usuario usuario = registrarUsuarioUseCase.registrar(registro);

        log.info("Usuario registrado: usuarioId={}", usuario.getId());

        return usuario;
    }
}
