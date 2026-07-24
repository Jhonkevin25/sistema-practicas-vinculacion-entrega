package ec.edu.unibe.sistema_practicas.evaluacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ENCUESTAS_SATISFACCION")
public class EncuestaSatisfaccion {

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

    @NotNull
    @Min(1)
    @Max(3)
    private Integer parcial;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "satisfaccion_tutor")
    private Integer satisfaccionTutor;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "satisfaccion_empresa_proyecto")
    private Integer satisfaccionEmpresaProyecto;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "relacion_carrera")
    private Integer relacionCarrera;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "claridad_instrucciones")
    private Integer claridadInstrucciones;

    private String comentario;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;
}
