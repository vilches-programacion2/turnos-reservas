package reservas.usuario.application.usecases;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reservas.usuario.application.exception.EmailYaRegistradoException;
import reservas.usuario.application.exception.LoginYaRegistradoException;
import reservas.usuario.domain.model.RegistroUsuario;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.PasswordHasher;
import reservas.usuario.domain.ports.out.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarUsuarioUseCaseImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private RegistrarUsuarioUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegistrarUsuarioUseCaseImpl(
                usuarioRepository,
                passwordHasher
        );
    }

    @Test
    void debeGuardarUsuarioActivoConHashYRolDeUsuario() {
        RegistroUsuario registro = registroValido();

        when(passwordHasher.hash(registro.getPassword()))
                .thenReturn("hash-de-prueba");

        Usuario persistido = Usuario.builder().id(1L).build();
        when(usuarioRepository.save(any(Usuario.class)))
                .thenReturn(persistido);

        Usuario resultado = useCase.registrar(registro);

        ArgumentCaptor<Usuario> captor =
                ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());

        Usuario enviado = captor.getValue();

        assertThat(enviado.getId()).isNull();
        assertThat(enviado.getLogin()).isEqualTo(registro.getLogin());
        assertThat(enviado.getEmail()).isEqualTo(registro.getEmail());
        assertThat(enviado.getFirstName()).isEqualTo(registro.getFirstName());
        assertThat(enviado.getLastName()).isEqualTo(registro.getLastName());
        assertThat(enviado.getImageUrl()).isEqualTo(registro.getImageUrl());
        assertThat(enviado.getLangKey()).isEqualTo(registro.getLangKey());
        assertThat(enviado.getPasswordHash()).isEqualTo("hash-de-prueba");
        assertThat(enviado.isActivated()).isTrue();
        assertThat(enviado.getAuthorities()).containsExactly("ROLE_USER");
        assertThat(enviado.getCreatedAt()).isNotNull();
        assertThat(enviado.getUpdatedAt()).isEqualTo(enviado.getCreatedAt());
        assertThat(resultado).isSameAs(persistido);
    }

    @Test
    void debeRechazarLoginExistenteSinGuardarNiHashear() {
        RegistroUsuario registro = registroValido();

        when(usuarioRepository.existsByLoginIgnoreCase(registro.getLogin()))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.registrar(registro))
                .isInstanceOf(LoginYaRegistradoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void debeRechazarEmailExistenteSinGuardarNiHashear() {
        RegistroUsuario registro = registroValido();

        when(usuarioRepository.existsByEmailIgnoreCase(registro.getEmail()))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.registrar(registro))
                .isInstanceOf(EmailYaRegistradoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verifyNoInteractions(passwordHasher);
    }

    private RegistroUsuario registroValido() {
        return RegistroUsuario.builder()
                .login("josias")
                .password("Una-clave-de-prueba-2026")
                .firstName("Josías")
                .lastName("Vilches")
                .email("josias@example.com")
                .imageUrl("https://example.com/avatar.png")
                .langKey("es")
                .build();
    }
}