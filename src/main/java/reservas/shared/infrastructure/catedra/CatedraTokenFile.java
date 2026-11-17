package reservas.shared.infrastructure.catedra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatedraTokenFile {
    private static final int MAX_TOKEN_BYTES = 16384;
    private final CatedraProperties properties;

    public String leer() throws IOException {
        if (!properties.isEnabled() || properties.getTokenFile() == null
                || !Files.isRegularFile(properties.getTokenFile())) {
            throw new IOException("Credencial técnica no disponible.");
        }
        try (var input = Files.newInputStream(properties.getTokenFile())) {
            byte[] bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
            String token = new String(bytes, StandardCharsets.UTF_8).strip();
            if (bytes.length > MAX_TOKEN_BYTES || !token.matches("[A-Za-z0-9._~+/=-]+")) {
                throw new IOException("Credencial técnica inválida.");
            }
            return token;
        }
    }
}
