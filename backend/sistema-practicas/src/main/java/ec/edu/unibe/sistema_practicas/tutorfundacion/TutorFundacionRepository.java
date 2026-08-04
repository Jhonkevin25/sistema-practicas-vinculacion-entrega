package ec.edu.unibe.sistema_practicas.tutorfundacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TutorFundacionRepository extends JpaRepository<TutorFundacion, Integer> {
    List<TutorFundacion> findByFundacionIdOrderByNombreAsc(Integer fundacionId);
    List<TutorFundacion> findByFundacionIdAndActivoTrueOrderByNombreAsc(Integer fundacionId);
    Optional<TutorFundacion> findByUsuarioIdAndFundacionId(Integer usuarioId, Integer fundacionId);
}
