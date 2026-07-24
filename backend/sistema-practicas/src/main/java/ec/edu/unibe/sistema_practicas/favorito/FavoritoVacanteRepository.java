package ec.edu.unibe.sistema_practicas.favorito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoritoVacanteRepository extends JpaRepository<FavoritoVacante, Integer> {
    List<FavoritoVacante> findByEstudianteId(Integer estudianteId);
    Optional<FavoritoVacante> findByEstudianteIdAndVacanteId(Integer estudianteId, Integer vacanteId);
}
