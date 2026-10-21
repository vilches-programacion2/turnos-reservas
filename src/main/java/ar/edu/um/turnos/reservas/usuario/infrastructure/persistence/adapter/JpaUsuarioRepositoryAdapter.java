package ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.adapter;

import ar.edu.um.turnos.reservas.usuario.domain.model.Usuario;
import ar.edu.um.turnos.reservas.usuario.domain.ports.out.UsuarioRepository;
import ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.repository.JpaUsuarioRepository;
import ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.mapper.UsuarioMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaUsuarioRepositoryAdapter implements UsuarioRepository {
    private final JpaUsuarioRepository repository;
    private final UsuarioMapper mapper;

    @Override
    @Transactional
    public Usuario save(Usuario value) {
        return mapper.toDomainModel(repository.saveAndFlush(mapper.toEntity(value)));
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return repository.findById(id).map(mapper::toDomainModel);
    }

    @Override
    public Optional<Usuario> findByLoginIgnoreCase(String login) {
        return repository.findByLoginIgnoreCase(login).map(mapper::toDomainModel);
    }
}
