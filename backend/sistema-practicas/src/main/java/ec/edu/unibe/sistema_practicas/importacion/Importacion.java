package ec.edu.unibe.sistema_practicas.importacion;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "IMPORTACIONES")
public class Importacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archivo_nombre", nullable = false, length = 255)
    private String archivoNombre;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "filas_total", nullable = false)
    private Integer filasTotal;

    @Column(name = "filas_ok", nullable = false)
    private Integer filasOk;

    @Column(name = "filas_error", nullable = false)
    private Integer filasError;

    @Column(nullable = false)
    private Integer creados;

    @Column(nullable = false)
    private Integer actualizados;

    @Column(nullable = false)
    private Integer enlazados;

    @Column(nullable = false, length = 20)
    private String estado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle_errores", nullable = false, columnDefinition = "jsonb")
    private String detalleErrores;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
