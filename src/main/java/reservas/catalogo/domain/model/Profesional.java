package reservas.catalogo.domain.model;

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
public class Profesional {
    private Long id;
    private Long categoryId;
    private String firstName;
    private String lastName;
    private Boolean enabled;
}
