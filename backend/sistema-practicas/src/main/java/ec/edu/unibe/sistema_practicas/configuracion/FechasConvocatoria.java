package ec.edu.unibe.sistema_practicas.configuracion;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "FECHAS_CONVOCATORIA")
public class FechasConvocatoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "periodo_academico")
    private String periodoAcademico;

    @NotBlank
    private String tipo;

    @NotNull
    @Column(name = "convocatoria_inicio")
    private LocalDate convocatoriaInicio;

    @NotNull
    @Column(name = "convocatoria_fin")
    private LocalDate convocatoriaFin;

    @NotNull
    @Column(name = "fecha_limite_documentos")
    private LocalDate fechaLimiteDocumentos;

    @NotNull
    @Column(name = "fecha_inicio_postulacion")
    private LocalDate fechaInicioPostulacion;

    @ManyToOne
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
