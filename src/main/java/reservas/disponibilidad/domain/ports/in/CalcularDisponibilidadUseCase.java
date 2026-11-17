package reservas.disponibilidad.domain.ports.in;

import java.time.LocalDate;
import reservas.disponibilidad.domain.model.DisponibilidadProfesional;

public interface CalcularDisponibilidadUseCase {
    DisponibilidadProfesional calcular(Long professionalId, LocalDate date);
}
