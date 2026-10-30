package reservas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Claves efímeras solo para pruebas, fuera del repositorio. */
final class JwtTestKeys {
    private static final Path PRIVATE_KEY;
    private static final Path PUBLIC_KEY;

    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("reservas-jwt-test-");
            directory.toFile().deleteOnExit();
            PRIVATE_KEY = escribir(directory, "private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
            PUBLIC_KEY = escribir(directory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private JwtTestKeys() {
    }

    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.private-key", () -> PRIVATE_KEY.toUri().toString());
        registry.add("app.jwt.public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("app.jwt.issuer", () -> "https://reservas.test");
        registry.add("app.jwt.audience", () -> "turnos-reservas-test");
        registry.add("app.jwt.time-to-live", () -> "30m");
    }

    private static Path escribir(Path directory, String filename, String type, byte[] bytes) throws Exception {
        Path file = directory.resolve(filename);
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes);
        Files.writeString(file, "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n");
        file.toFile().deleteOnExit();
        return file;
    }
}
