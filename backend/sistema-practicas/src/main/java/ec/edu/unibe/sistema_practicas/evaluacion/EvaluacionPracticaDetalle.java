package ec.edu.unibe.sistema_practicas.evaluacion;

import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "EVALUACIONES_PRACTICAS_DETALLE")
public class EvaluacionPracticaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "practica_id")
    private Practica practica;

    @ManyToOne
    @JoinColumn(name = "vinculacion_id")
    private Vinculacion vinculacion;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer parcial;

    // Sin inicializadores: un campo ausente en el JSON debe llegar como null
    // para que el upsert distinga "no enviado" de "enviado en 0"
    @Column(name = "nota_tutor", columnDefinition = "numeric")
    private Double notaTutor;

    @Column(name = "nota_coord", columnDefinition = "numeric")
    private Double notaCoord;

    @Column(name = "nota_final", columnDefinition = "numeric")
    private Double notaFinal;

    @Column(name = "encuesta_completada")
    private Boolean encuestaCompletada;
}
