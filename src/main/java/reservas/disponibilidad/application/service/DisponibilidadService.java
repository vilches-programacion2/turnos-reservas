package reservas.disponibilidad.application.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.disponibilidad.domain.model.DisponibilidadProfesional;
import reservas.disponibilidad.domain.ports.in.CalcularDisponibilidadUseCase;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {
    private final CalcularDisponibilidadUseCase calcularDisponibilidad;

    public DisponibilidadProfesional calcular(Long professionalId, LocalDate date) {
        return calcularDisponibilidad.calcular(professionalId, date);
    }
}
