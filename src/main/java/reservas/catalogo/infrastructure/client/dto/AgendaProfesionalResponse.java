package reservas.catalogo.infrastructure.client.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgendaProfesionalResponse {
    private Long versionAplicada;
    private ProfesionalResponse professional;
    private List<HorarioSemanalResponse> weeklySchedules;
}
