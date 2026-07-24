package ec.edu.unibe.sistema_practicas.notacoordinacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "NOTAS_COORDINACION")
public class NotaCoordinacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "practica_id")
    private Practica practica;

    @ManyToOne
    @JoinColumn(name = "vinculacion_id")
    private Vinculacion vinculacion;

    @ManyToOne
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @NotBlank
    private String mensaje;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
