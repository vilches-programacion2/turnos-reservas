package reservas.disponibilidad.infrastructure.web.mapper;

import org.springframework.stereotype.Component;
import reservas.disponibilidad.domain.model.DisponibilidadProfesional;
import reservas.disponibilidad.infrastructure.web.dto.DisponibilidadResponse;

@Component
public class DisponibilidadDtoMapper {
    public DisponibilidadResponse toResponse(DisponibilidadProfesional disponibilidad) {
        if (disponibilidad == null) return null;
        return DisponibilidadResponse.builder().professionalId(disponibilidad.getProfessionalId())
                .date(disponibilidad.getDate()).versionAplicada(disponibilidad.getVersionAplicada())
                .availableSlots(disponibilidad.getAvailableSlots()).build();
    }
}
