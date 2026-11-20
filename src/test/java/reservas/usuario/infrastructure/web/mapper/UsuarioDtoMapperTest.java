package reservas.usuario.infrastructure.web.mapper;

import org.junit.jupiter.api.Test;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.infrastructure.web.dto.UsuarioResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioDtoMapperTest {

    private final UsuarioDtoMapper mapper = new UsuarioDtoMapper();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void debeMapearLosDatosPublicosDelUsuario() {
        Usuario usuario = usuarioRegistrado();

        UsuarioResponse response = mapper.toResponse(usuario);

        assertThat(response.getId()).isEqualTo(usuario.getId());
        assertThat(response.getLogin()).isEqualTo(usuario.getLogin());
        assertThat(response.getFirstName()).isEqualTo(usuario.getFirstName());
        assertThat(response.getLastName()).isEqualTo(usuario.getLastName());
        assertThat(response.getEmail()).isEqualTo(usuario.getEmail());
        assertThat(response.getImageUrl()).isEqualTo(usuario.getImageUrl());
        assertThat(response.getLangKey()).isEqualTo(usuario.getLangKey());
        assertThat(response.isActivated()).isTrue();
    }

    @Test
    void debeExcluirLaPasswordYElHashDelJson() {
        Usuario usuario = usuarioRegistrado();
        UsuarioResponse response = mapper.toResponse(usuario);

        String json = jsonMapper.writeValueAsString(response);
        var contenido = jsonMapper.readTree(json);

        assertThat(contenido.has("password")).isFalse();
        assertThat(contenido.has("passwordHash")).isFalse();
        assertThat(json).doesNotContain(usuario.getPasswordHash());
        assertThat(contenido.get("login").asText()).isEqualTo("josias");
    }

    @Test
    void debeRetornarNullCuandoNoHayUsuario() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    private Usuario usuarioRegistrado() {
        return Usuario.builder()
                .id(1L)
                .login("josias")
                .passwordHash("hash-interno-que-no-debe-exponerse")
                .firstName("Josías")
                .lastName("Vilches")
                .email("josias@example.com")
                .imageUrl("https://example.com/avatar.png")
                .langKey("es")
                .activated(true)
                .build();
    }
}