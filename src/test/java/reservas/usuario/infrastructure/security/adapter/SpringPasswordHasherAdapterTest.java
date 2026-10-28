package reservas.usuario.infrastructure.security.adapter;

import org.junit.jupiter.api.Test;
import reservas.usuario.infrastructure.security.config.PasswordConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPasswordHasherAdapterTest {

    private final SpringPasswordHasherAdapter passwordHasher =
            new SpringPasswordHasherAdapter(
                    new PasswordConfiguration().passwordEncoder()
            );

    @Test
    void debeVerificarLaPasswordCorrectaYRechazarOtra() {
        String password = "Una-clave-de-prueba-2026";
        String hash = passwordHasher.hash(password);

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).isNotEqualTo(password);
        assertThat(passwordHasher.matches(password, hash)).isTrue();
        assertThat(passwordHasher.matches("Otra-clave", hash)).isFalse();
    }

    @Test
    void debeGenerarHashesDistintosParaLaMismaPassword() {
        String password = "Una-clave-de-prueba-2026";

        String primerHash = passwordHasher.hash(password);
        String segundoHash = passwordHasher.hash(password);

        assertThat(primerHash).isNotEqualTo(segundoHash);
        assertThat(passwordHasher.matches(password, primerHash)).isTrue();
        assertThat(passwordHasher.matches(password, segundoHash)).isTrue();
    }
}