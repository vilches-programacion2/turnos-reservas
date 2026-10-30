package reservas.usuario.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.UsuarioRepository;
import reservas.usuario.infrastructure.persistence.mapper.UsuarioMapper;
import reservas.usuario.infrastructure.persistence.repository.JpaUsuarioRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import reservas.usuario.application.exception.EmailYaRegistradoException;
import reservas.usuario.application.exception.LoginYaRegistradoException;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaUsuarioRepositoryAdapter implements UsuarioRepository {

    private final JpaUsuarioRepository repository;
    private final UsuarioMapper mapper;

    @Override
    @Transactional
    public Usuario save(Usuario value) {
        try {
            return mapper.toDomainModel(
                    repository.saveAndFlush(mapper.toEntity(value))
            );
        } catch (DataIntegrityViolationException exception) {
            throw traducirConflicto(exception);
        }
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return repository.findById(id)
                .map(mapper::toDomainModel);
    }

    @Override
    public Optional<Usuario> findByLoginIgnoreCase(String login) {
        return repository.findByLoginIgnoreCase(login)
                .map(mapper::toDomainModel);
    }

    @Override
    public boolean existsByLoginIgnoreCase(String login) {
        return repository.existsByLoginIgnoreCase(login);
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }
    private RuntimeException traducirConflicto(
            DataIntegrityViolationException exception
    ) {
        Throwable causa = exception;

        while (causa != null) {
            if (causa instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())) {

                String nombre = constraint.getConstraintName();

                if ("ux_app_user_login".equals(nombre)) {
                    return new LoginYaRegistradoException();
                }

                if ("ux_app_user_email".equals(nombre)) {
                    return new EmailYaRegistradoException();
                }
            }

            causa = causa.getCause();
        }

        return exception;
    }
}