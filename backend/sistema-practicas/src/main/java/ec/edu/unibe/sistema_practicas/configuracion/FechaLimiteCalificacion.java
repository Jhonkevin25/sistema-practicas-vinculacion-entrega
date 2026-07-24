package ec.edu.unibe.sistema_practicas.configuracion;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "FECHAS_LIMITE_CALIFICACION")
public class FechaLimiteCalificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "periodo_academico")
    private String periodoAcademico;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer parcial;

    @NotNull
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;
}
