package ec.edu.unibe.sistema_practicas.postulacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "POSTULACIONES_MERITOCRATICAS")
public class PostulacionMeritocratica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @Column(columnDefinition="numeric")
    private Double promedio;

    @ManyToOne
    @JoinColumn(name = "pref1_id", nullable = false)
    private VacantePractica pref1;

    @ManyToOne
    @JoinColumn(name = "pref2_id", nullable = false)
    private VacantePractica pref2;

    @ManyToOne
    @JoinColumn(name = "pref3_id")
    private VacantePractica pref3;

    @Column(columnDefinition="numeric")
    private Double score;

    private String estado = "Pendiente";

    @ManyToOne
    @JoinColumn(name = "asignado_empresa_id")
    private Empresa asignadoEmpresa;

    @ManyToOne
    @JoinColumn(name = "asignado_vacante_id")
    private VacantePractica asignadoVacante;

    @Column(name = "ajustado_manualmente")
    private Boolean ajustadoManualmente = false;

    @Column(name = "justificacion_ajuste")
    private String justificacionAjuste;
}


