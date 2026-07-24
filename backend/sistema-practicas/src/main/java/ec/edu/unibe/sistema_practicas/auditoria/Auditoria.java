package ec.edu.unibe.sistema_practicas.auditoria;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tabla_afectada", nullable = false)
    private String tablaAfectada;

    @Column(nullable = false)
    private String accion;

    // Columnas jsonb; se almacenan como texto JSON valido
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_antes")
    private String datosAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_despues")
    private String datosDespues;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fecha;
}
