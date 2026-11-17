package reservas.disponibilidad.infrastructure.client.mapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException.Error;
import reservas.disponibilidad.infrastructure.client.dto.OcupacionesResponse;

@Component
public class OcupacionesMapper {
    public Set<LocalTime> toDomain(OcupacionesResponse response, Long professionalId, LocalDate date) {
        if (response == null || !professionalId.equals(response.getProfessionalId())
                || !date.equals(response.getFrom()) || !date.equals(response.getTo())
                || response.getOccupancies() == null) {
            throw new ConsultaOcupacionesException(Error.RESPUESTA_INVALIDA);
        }
        var ocupadas = new HashSet<LocalTime>();
        for (var ocupacion : response.getOccupancies()) {
            if (ocupacion == null || !date.equals(ocupacion.getDate()) || ocupacion.getStartTime() == null
                    || !("CONFIRMED".equals(ocupacion.getStatus()) || "HELD".equals(ocupacion.getStatus()))
                    || ("HELD".equals(ocupacion.getStatus()) && ocupacion.getExpiresAt() == null)
                    || ("CONFIRMED".equals(ocupacion.getStatus()) && ocupacion.getExpiresAt() != null)) {
                throw new ConsultaOcupacionesException(Error.RESPUESTA_INVALIDA);
            }
            // El servidor central determina si el hold sigue activo; no se expira con nuestro reloj.
            ocupadas.add(ocupacion.getStartTime());
        }
        return Set.copyOf(ocupadas);
    }
}
