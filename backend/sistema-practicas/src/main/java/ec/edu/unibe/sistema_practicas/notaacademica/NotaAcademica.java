package ec.edu.unibe.sistema_practicas.notaacademica;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "NOTAS_ACADEMICAS")
public class NotaAcademica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @Column(nullable = false)
    private String carrera;

    @Column(nullable = false)
    private Integer semestre;

    @Column(name = "periodo_academico", nullable = false)
    private String periodoAcademico;

    @Column(columnDefinition = "numeric", nullable = false)
    private Double promedio;

    @Column(name = "documento_url")
    private String documentoUrl;

    @Column(nullable = false)
    private String fuente = "MANUAL";

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "email_institucional")
    private String emailInstitucional;

    @Column(nullable = false)
    private String estado = "PENDIENTE";

    private String observaciones;

    @ManyToOne
    @JoinColumn(name = "verificado_por")
    private Usuario verificadoPor;

    @Column(name = "fecha_verificacion")
    private LocalDateTime fechaVerificacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
