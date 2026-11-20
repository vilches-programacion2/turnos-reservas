package reservas.usuario.infrastructure.persistence.mapper;

import reservas.usuario.domain.model.Usuario;
import reservas.usuario.infrastructure.persistence.entity.UsuarioEntity;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {
    public UsuarioEntity toEntity(Usuario source) {
        if (source == null) {
            return null;
        }
        return UsuarioEntity.builder()
                .id(source.getId())
                .login(source.getLogin())
                .passwordHash(source.getPasswordHash())
                .firstName(source.getFirstName())
                .lastName(source.getLastName())
                .email(source.getEmail())
                .imageUrl(source.getImageUrl())
                .langKey(source.getLangKey())
                .activated(source.isActivated())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .authorities(new HashSet<>(source.getAuthorities()))
                .build();
    }

    public Usuario toDomainModel(UsuarioEntity source) {
        if (source == null) {
            return null;
        }
        return Usuario.builder()
                .id(source.getId())
                .login(source.getLogin())
                .passwordHash(source.getPasswordHash())
                .firstName(source.getFirstName())
                .lastName(source.getLastName())
                .email(source.getEmail())
                .imageUrl(source.getImageUrl())
                .langKey(source.getLangKey())
                .activated(source.isActivated())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .authorities(new HashSet<>(source.getAuthorities()))
                .build();
    }

}
