package reservas.usuario.infrastructure.security.adapter;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;
import reservas.usuario.domain.model.Usuario;
import reservas.usuario.domain.ports.out.EmisorToken;
import reservas.usuario.infrastructure.security.config.JwtProperties;

@Component
@RequiredArgsConstructor
public class JwtEmisorTokenAdapter implements EmisorToken {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    @Override
    public String emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(usuario.getId().toString())
                .issuedAt(ahora).expiresAt(ahora.plus(properties.timeToLive()))
                .claim("roles", List.copyOf(usuario.getAuthorities())).build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
