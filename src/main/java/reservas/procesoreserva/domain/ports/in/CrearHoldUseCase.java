package reservas.procesoreserva.domain.ports.in;

import java.time.LocalDate;
import java.time.LocalTime;
import reservas.procesoreserva.domain.model.ProcesoReserva;

public interface CrearHoldUseCase {
    ProcesoReserva crear(Long userId, Long professionalId, LocalDate date, LocalTime startTime);
}
