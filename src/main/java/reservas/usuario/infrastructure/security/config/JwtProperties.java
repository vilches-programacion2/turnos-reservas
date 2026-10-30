package reservas.usuario.infrastructure.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        Duration timeToLive,
        @NotNull Resource privateKey,
        @NotNull Resource publicKey) {
    public JwtProperties {
        if (timeToLive == null || timeToLive.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("La duración JWT debe ser al menos un segundo.");
        }
    }
}
