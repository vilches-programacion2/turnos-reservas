package reservas.disponibilidad.domain.ports.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public interface OcupacionesClient {
    Set<LocalTime> consultar(Long professionalId, LocalDate date);
}
