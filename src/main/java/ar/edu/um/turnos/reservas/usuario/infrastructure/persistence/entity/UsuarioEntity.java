package ar.edu.um.turnos.reservas.usuario.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_user")
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "login", nullable = false, length = 50)
    private String login;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "first_name", nullable = true, length = 100)
    private String firstName;
    @Column(name = "last_name", nullable = true, length = 100)
    private String lastName;
    @Column(name = "email", nullable = true, length = 254)
    private String email;
    @Column(name = "image_url", nullable = true, length = 256)
    private String imageUrl;
    @Column(name = "lang_key", nullable = true, length = 10)
    private String langKey;
    @Column(name = "activated", nullable = false)
    private boolean activated;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_authority", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "authority", nullable = false, length = 50)
    @Builder.Default
    private Set<String> authorities = new HashSet<>();
}
