package ec.edu.unibe.sistema_practicas.empresa;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "El RUC es obligatorio")
    @Column(nullable = false, unique = true, length = 13)
    private String ruc;

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "cupos_disponibles")
    private Integer cuposDisponibles = 0;

    private Boolean activo = true;

    @Column(name = "tiene_convenio")
    private Boolean tieneConvenio = false;
}
