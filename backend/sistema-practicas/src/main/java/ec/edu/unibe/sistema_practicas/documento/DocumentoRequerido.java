package ec.edu.unibe.sistema_practicas.documento;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "DOCUMENTOS_REQUERIDOS")
public class DocumentoRequerido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String proceso;
    private String carrera;
    private String etapa;

    @Column(name = "tipo_documento")
    private String tipoDocumento;

    private String nombre;
    private String descripcion;
    private Boolean obligatorio = true;
    private String momento = "PRE_POSTULACION";
    private Boolean activo = true;

    @ManyToOne
    @JoinColumn(name = "solicitado_por")
    private Usuario solicitadoPor;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
