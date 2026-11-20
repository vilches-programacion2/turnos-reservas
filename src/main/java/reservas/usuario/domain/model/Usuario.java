package reservas.usuario.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
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
public class Usuario {
    private Long id;
    private String login;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String email;
    private String imageUrl;
    private String langKey;
    private boolean activated;
    private Instant createdAt;
    private Instant updatedAt;
    @Builder.Default
    private Set<String> authorities = new HashSet<>();
}
