package reservas.disponibilidad.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DisponibilidadConfiguration {
    @Bean
    Clock disponibilidadClock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
