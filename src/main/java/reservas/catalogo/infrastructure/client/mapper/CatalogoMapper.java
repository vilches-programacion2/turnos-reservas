package reservas.catalogo.infrastructure.client.mapper;

import org.springframework.stereotype.Component;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.application.exception.ConsultaCatalogoException.Error;
import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.model.HorarioSemanal;
import reservas.catalogo.domain.model.Profesional;
import reservas.catalogo.infrastructure.client.dto.AgendaProfesionalResponse;
import reservas.catalogo.infrastructure.client.dto.HorarioSemanalResponse;
import reservas.catalogo.infrastructure.client.dto.ProfesionalResponse;

@Component
public class CatalogoMapper {
    public Profesional toDomain(ProfesionalResponse response, Long idSolicitado) {
        if (response == null || !idSolicitado.equals(response.getId())
                || !positivo(response.getCategoryId()) || response.getFirstName() == null
                || response.getLastName() == null || response.getEnabled() == null) {
            throw new ConsultaCatalogoException(Error.RESPUESTA_INVALIDA);
        }
        return Profesional.builder().id(response.getId()).categoryId(response.getCategoryId())
                .firstName(response.getFirstName()).lastName(response.getLastName())
                .enabled(response.getEnabled()).build();
    }

    public AgendaProfesional toDomain(AgendaProfesionalResponse response, Long idSolicitado) {
        if (response == null || response.getVersionAplicada() == null || response.getVersionAplicada() < 0
                || response.getWeeklySchedules() == null) {
            throw new ConsultaCatalogoException(Error.RESPUESTA_INVALIDA);
        }
        var profesional = toDomain(response.getProfessional(), idSolicitado);
        if (!profesional.getEnabled()) {
            throw new ConsultaCatalogoException(Error.RESPUESTA_INVALIDA);
        }
        return AgendaProfesional.builder().versionAplicada(response.getVersionAplicada())
                .professional(profesional)
                .weeklySchedules(response.getWeeklySchedules().stream().map(this::toDomain).toList()).build();
    }

    private HorarioSemanal toDomain(HorarioSemanalResponse response) {
        if (response == null || !positivo(response.getId()) || response.getDayOfWeek() == null
                || response.getStartTime() == null || response.getEndTime() == null
                || response.getSlotDurationMinutes() == null || response.getSlotDurationMinutes() <= 0) {
            throw new ConsultaCatalogoException(Error.RESPUESTA_INVALIDA);
        }
        return HorarioSemanal.builder().id(response.getId()).dayOfWeek(response.getDayOfWeek())
                .startTime(response.getStartTime()).endTime(response.getEndTime())
                .slotDurationMinutes(response.getSlotDurationMinutes()).build();
    }

    private boolean positivo(Long value) {
        return value != null && value > 0;
    }
}
