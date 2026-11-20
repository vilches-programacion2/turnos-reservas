package reservas.catalogo.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catalogo")
public record CatalogoProperties(URI baseUrl, String audience, Duration connectTimeout, Duration readTimeout) {
    public CatalogoProperties {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("Se requiere la audiencia de catálogo.");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Los timeouts de catálogo deben ser positivos.");
        }
        if (baseUrl != null && !baseUrl.toString().isBlank()
                && (baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme())))) {
            throw new IllegalArgumentException("La URL de catálogo debe ser HTTP(S) sin credenciales ni query.");
        }
    }

    public boolean configurado() {
        return baseUrl != null && !baseUrl.toString().isBlank();
    }
}
