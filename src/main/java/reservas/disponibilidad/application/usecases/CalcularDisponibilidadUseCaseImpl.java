package reservas.disponibilidad.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.catalogo.domain.ports.in.ConsultarAgendaProfesionalUseCase;
import reservas.disponibilidad.application.exception.SolicitudDisponibilidadInvalidaException;
import reservas.disponibilidad.domain.model.DisponibilidadProfesional;
import reservas.disponibilidad.domain.ports.in.CalcularDisponibilidadUseCase;
import reservas.disponibilidad.domain.ports.out.OcupacionesClient;

@Component
@RequiredArgsConstructor
public class CalcularDisponibilidadUseCaseImpl implements CalcularDisponibilidadUseCase {
    private final ConsultarAgendaProfesionalUseCase agenda;
    private final OcupacionesClient ocupaciones;
    private final Clock disponibilidadClock;

    @Override
    public DisponibilidadProfesional calcular(Long professionalId, LocalDate date) {
        if (professionalId == null || professionalId <= 0 || date == null
                || date.isBefore(LocalDate.now(disponibilidadClock))) {
            throw new SolicitudDisponibilidadInvalidaException();
        }
        var horarios = agenda.consultar(professionalId);
        var candidatos = new TreeSet<LocalTime>();
        for (var horario : horarios.getWeeklySchedules()) {
            if (horario.getDayOfWeek() != date.getDayOfWeek()) continue;
            long segundos = (long) horario.getSlotDurationMinutes() * 60;
            // Acotar primero la duración evita overflow y el retorno de LocalTime a medianoche.
            long inicio = horario.getStartTime().toNanoOfDay();
            long fin = horario.getEndTime().toNanoOfDay();
            if (segundos > (fin - inicio) / 1_000_000_000L) continue;
            long paso = segundos * 1_000_000_000L;
            for (long actual = inicio; actual <= fin - paso; actual += paso) {
                candidatos.add(LocalTime.ofNanoOfDay(actual));
            }
        }
        Set<LocalTime> ocupadas = candidatos.isEmpty() ? Set.of() : ocupaciones.consultar(professionalId, date);
        var ahora = LocalDateTime.now(disponibilidadClock);
        List<LocalTime> libres = candidatos.stream().filter(inicio -> !ocupadas.contains(inicio))
                .filter(inicio -> !date.atTime(inicio).isBefore(ahora)).toList();
        return DisponibilidadProfesional.builder().professionalId(professionalId).date(date)
                .versionAplicada(horarios.getVersionAplicada()).availableSlots(libres).build();
    }
}
