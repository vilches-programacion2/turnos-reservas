package reservas.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Registro público; las demás operaciones de negocio se habilitarán con JWT en RES-04. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/register")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health",
                                "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // Sin usuarios ni contraseña generada en logs; se reemplaza en RES-04.
        return new InMemoryUserDetailsManager();
    }
}
