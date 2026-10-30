package reservas.usuario.application.usecases;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reservas.usuario.application.exception.CredencialesInvalidasException;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.EmisorToken;
import reservas.usuario.domain.ports.out.PasswordHasher;
import reservas.usuario.domain.ports.out.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticarUsuarioUseCaseImplTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private PasswordHasher passwordHasher;
    @Mock private EmisorToken emisorToken;
    @InjectMocks private AutenticarUsuarioUseCaseImpl useCase;

    @Test
    void emiteTokenSoloConCredencialesValidasYUsuarioActivo() {
        var usuario = usuario();
        when(usuarios.findByLoginIgnoreCase("demo")).thenReturn(Optional.of(usuario));
        when(passwordHasher.matches("clave", "hash")).thenReturn(true);
        when(emisorToken.emitir(usuario)).thenReturn("token-de-prueba");
        assertThat(useCase.autenticar("demo", "clave")).isEqualTo("token-de-prueba");
    }

    @Test
    void rechazaPasswordIncorrectaSinEmitirToken() {
        when(usuarios.findByLoginIgnoreCase("demo")).thenReturn(Optional.of(usuario()));
        assertThatThrownBy(() -> useCase.autenticar("demo", "incorrecta"))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Credenciales inválidas.");
        verifyNoInteractions(emisorToken);
    }

    @Test
    void rechazaUsuarioInexistenteConElMismoError() {
        when(usuarios.findByLoginIgnoreCase("demo")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.autenticar("demo", "clave"))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Credenciales inválidas.");
        verifyNoInteractions(emisorToken);
    }

    @Test
    void rechazaUsuarioInactivoSinEmitirToken() {
        var usuario = usuario();
        usuario.setActivated(false);
        when(usuarios.findByLoginIgnoreCase("demo")).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> useCase.autenticar("demo", "clave"))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Credenciales inválidas.");
        verifyNoInteractions(emisorToken);
    }

    private Usuario usuario() {
        return Usuario.builder().id(1L).login("demo").activated(true).passwordHash("hash").build();
    }
}
