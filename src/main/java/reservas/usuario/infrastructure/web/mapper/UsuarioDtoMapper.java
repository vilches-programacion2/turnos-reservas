package reservas.usuario.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import reservas.usuario.domain.model.RegistroUsuario;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.infrastructure.web.dto.RegistroUsuarioRequest;
import reservas.usuario.infrastructure.web.dto.UsuarioResponse;

@Component
public class UsuarioDtoMapper {

    public RegistroUsuario toDomain(RegistroUsuarioRequest request) {
        if (request == null) {
            return null;
        }

        return RegistroUsuario.builder()
                .login(request.getLogin())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .imageUrl(request.getImageUrl())
                .langKey(request.getLangKey())
                .build();
    }

    public UsuarioResponse toResponse(Usuario usuario) {
        if (usuario == null) {
            return null;
        }

        return UsuarioResponse.builder()
                .id(usuario.getId())
                .login(usuario.getLogin())
                .firstName(usuario.getFirstName())
                .lastName(usuario.getLastName())
                .email(usuario.getEmail())
                .imageUrl(usuario.getImageUrl())
                .langKey(usuario.getLangKey())
                .activated(usuario.isActivated())
                .build();
    }
}
