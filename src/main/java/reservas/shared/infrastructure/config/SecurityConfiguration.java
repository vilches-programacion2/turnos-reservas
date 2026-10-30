package reservas.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** APIs sin sesión: credenciales solo en login y JWT Bearer para consultar la cuenta. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(authorities);
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/register"),
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/authenticate")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/register", "/api/authenticate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health",
                                "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/account").hasRole("USER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
                .build();
    }
}
