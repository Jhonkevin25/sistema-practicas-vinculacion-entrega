package ec.edu.unibe.sistema_practicas.usuario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String cedula;
    private String nombre;
    private String apellido;
    private String email;

    // WRITE_ONLY: se acepta en el body de create/update pero nunca se serializa en respuestas
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash")
    private String passwordHash;

    private Boolean activo;

    @Column(name = "primer_login")
    private Boolean primerLogin;

    @JsonIgnore
    @Column(name = "intentos_login_fallidos", nullable = false)
    private Integer intentosLoginFallidos = 0;

    @JsonIgnore
    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    private String fuente = "MANUAL";

    @Column(name = "external_id")
    private String externalId;

    // Solo aplica a usuarios con rol TUTOR: PRACTICAS | VINCULACION | AMBOS
    @Column(name = "tutor_tipo")
    private String tutorTipo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "usuarios_roles",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Rol> roles = new HashSet<>();
}
