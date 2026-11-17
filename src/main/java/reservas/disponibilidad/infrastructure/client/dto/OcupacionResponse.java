package reservas.disponibilidad.infrastructure.client.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcupacionResponse {
    private LocalDate date;
    private LocalTime startTime;
    private String status;
    private Instant expiresAt;
}
