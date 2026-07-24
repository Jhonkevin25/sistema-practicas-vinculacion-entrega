package ec.edu.unibe.sistema_practicas.fundacion;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "fundaciones")
public class Fundacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 13)
    private String ruc;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String mision;

    @Column(name = "area_intervencion", length = 200)
    private String areaIntervencion;

    private Boolean activa = true;

    @Column(name = "tiene_convenio")
    private Boolean tieneConvenio = false;
}
