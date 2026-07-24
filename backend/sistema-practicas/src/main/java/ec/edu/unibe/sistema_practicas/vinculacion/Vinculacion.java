package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "vinculacion")
public class Vinculacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "fundacion_id", nullable = false)
    private Fundacion fundacion;

    @ManyToOne
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne
    @JoinColumn(name = "tutor_id")
    private Usuario tutor;

    @ManyToOne
    @JoinColumn(name = "encargado_id")
    private Usuario encargado;

    @Column(length = 30)
    private String estado = "pendiente";

    @Column(name = "horas_requeridas", nullable = false)
    private Integer horasRequeridas;

    @Column(name = "horas_completadas", nullable = false)
    private Integer horasCompletadas = 0;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "periodo_academico", length = 20)
    private String periodoAcademico;

    @ManyToOne
    @JoinColumn(name = "cerrado_por")
    private Usuario cerradoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cierre_snapshot")
    private String cierreSnapshot;

    @Column(name = "motivo_finalizacion")
    private String motivoFinalizacion;

    // Fase 42: liberación única del cupo de una vinculación retirada
    @Column(name = "cupo_liberado", nullable = false)
    private Boolean cupoLiberado = false;

    @ManyToOne
    @JoinColumn(name = "cupo_liberado_por")
    private Usuario cupoLiberadoPor;

    @Column(name = "cupo_liberado_en")
    private LocalDateTime cupoLiberadoEn;

    @Column(name = "motivo_liberacion_cupo")
    private String motivoLiberacionCupo;
}
