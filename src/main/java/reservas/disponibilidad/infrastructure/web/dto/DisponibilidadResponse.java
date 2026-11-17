package reservas.disponibilidad.infrastructure.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadResponse {
    private Long professionalId;
    private LocalDate date;
    private Long versionAplicada;
    private List<LocalTime> availableSlots;
}
