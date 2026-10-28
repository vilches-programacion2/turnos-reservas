package reservas.shared.infrastructure.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApplicationLifecycleLogger {
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Servicio turnos-reservas listo");
    }
}
