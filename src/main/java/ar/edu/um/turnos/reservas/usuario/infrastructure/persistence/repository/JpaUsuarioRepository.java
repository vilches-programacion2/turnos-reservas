package ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.repository;

import ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.entity.UsuarioEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByLoginIgnoreCase(String login);
}
