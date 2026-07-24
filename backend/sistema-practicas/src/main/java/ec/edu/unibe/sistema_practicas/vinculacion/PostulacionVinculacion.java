package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "POSTULACIONES_VINCULACION")
public class PostulacionVinculacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    private String estado = "Pendiente";

    @Column(name = "periodo_academico")
    private String periodoAcademico;

    @ManyToOne
    @JoinColumn(name = "vinculacion_id")
    private Vinculacion vinculacion;

    private String observacion;

    @ManyToOne
    @JoinColumn(name = "aprobado_por")
    private Usuario aprobadoPor;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
