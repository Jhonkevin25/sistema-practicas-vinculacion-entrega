package ec.edu.unibe.sistema_practicas.asistencia;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "ASISTENCIAS")
public class Asistencia {
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
    private LocalDate fecha;

    @Column(name = "hora_ingreso")
    private String horaIngreso;

    @Column(name = "hora_salida")
    private String horaSalida;

    private String estado;
    private String observaciones;
}
