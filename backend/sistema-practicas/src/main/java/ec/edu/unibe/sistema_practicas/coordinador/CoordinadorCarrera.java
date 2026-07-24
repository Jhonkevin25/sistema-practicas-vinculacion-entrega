package ec.edu.unibe.sistema_practicas.coordinador;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "coordinador_carreras")
public class CoordinadorCarrera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String carrera;

    // Se repite por fila para no alterar la tabla USUARIOS
    @Column(name = "coordinacion_tipo", nullable = false)
    private String coordinacionTipo;
}
