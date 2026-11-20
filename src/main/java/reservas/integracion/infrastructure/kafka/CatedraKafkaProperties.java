package reservas.integracion.infrastructure.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catedra.kafka")
public record CatedraKafkaProperties(String securityProtocol, Duration requestTimeout,
        Duration operationTimeout, Duration retryBackoff, int retries) {
    public CatedraKafkaProperties {
        if (securityProtocol == null || !java.util.Set.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL").contains(securityProtocol)
                || !plazoValido(requestTimeout) || !plazoValido(operationTimeout) || !plazoValido(retryBackoff)
                || operationTimeout.compareTo(requestTimeout) < 0 || retries < 0) {
            throw new IllegalArgumentException("Configuración Kafka de cátedra inválida.");
        }
    }

    private static boolean plazoValido(Duration plazo) {
        return plazo != null && plazo.toMillis() > 0 && plazo.toMillis() <= Integer.MAX_VALUE;
    }
}
