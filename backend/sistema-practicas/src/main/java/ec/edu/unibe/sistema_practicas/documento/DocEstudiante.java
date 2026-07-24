package ec.edu.unibe.sistema_practicas.documento;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "docs_estudiante")
public class DocEstudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "requerido_id")
    private DocumentoRequerido requerido;

    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @Column(name = "url_archivo")
    private String urlArchivo;

    private String proceso = "GENERAL";
    private String carrera;
    private String etapa;
    private String estado = "cargado";
    private String observacion;

    @ManyToOne
    @JoinColumn(name = "revisado_por")
    private ec.edu.unibe.sistema_practicas.usuario.Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "fecha_subida")
    private LocalDateTime fechaSubida;
}
