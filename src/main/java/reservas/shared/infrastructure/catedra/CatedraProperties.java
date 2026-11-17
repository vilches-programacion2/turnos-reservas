package reservas.shared.infrastructure.catedra;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.catedra")
public class CatedraProperties {
    private boolean enabled;
    private URI baseUrl;
    private Path tokenFile;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public void validar() {
        if (!positiva(connectTimeout) || !positiva(readTimeout)
                || (enabled && (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null || tokenFile == null))) {
            throw new IllegalStateException("Configuración de cátedra incompleta o inválida");
        }
    }

    private boolean positiva(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
