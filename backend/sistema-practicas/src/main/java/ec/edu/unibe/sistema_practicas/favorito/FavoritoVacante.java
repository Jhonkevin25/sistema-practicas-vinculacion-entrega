package ec.edu.unibe.sistema_practicas.favorito;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "favoritos_vacantes")
public class FavoritoVacante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "vacante_id", nullable = false)
    private VacantePractica vacante;
}
