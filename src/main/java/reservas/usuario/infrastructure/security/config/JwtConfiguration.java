package reservas.usuario.infrastructure.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties properties) throws IOException {
        try (var input = properties.publicKey().getInputStream()) {
            var key = RsaKeyConverters.x509().convert(input);
            if (key == null || key.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("La clave RSA debe tener al menos 2048 bits.");
            }
            return key;
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties, RSAPublicKey jwtPublicKey) throws IOException {
        RSAPrivateKey privateKey;
        try (var input = properties.privateKey().getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(input);
        }
        if (privateKey == null || !privateKey.getModulus().equals(jwtPublicKey.getModulus())) {
            throw new IllegalArgumentException("Las claves RSA de JWT no corresponden al mismo par.");
        }
        var key = new RSAKey.Builder(jwtPublicKey).privateKey(privateKey).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, RSAPublicKey jwtPublicKey) {
        var decoder = NimbusJwtDecoder.withPublicKey(jwtPublicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> contrato = jwt -> {
            boolean identidadValida;
            try {
                identidadValida = Long.parseLong(jwt.getSubject()) > 0;
            } catch (NumberFormatException exception) {
                identidadValida = false;
            }
            var roles = jwt.getClaims().get("roles");
            boolean rolesValidos = roles == null || roles instanceof List<?> lista
                    && lista.stream().allMatch(String.class::isInstance);
            if (identidadValida && rolesValidos
                    && jwt.getAudience() != null && jwt.getAudience().contains(properties.audience())
                    && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Claims JWT inválidos.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), contrato));
        return decoder;
    }
}
