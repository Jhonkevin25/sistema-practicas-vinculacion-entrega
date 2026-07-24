package ec.edu.unibe.sistema_practicas.notacoordinacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotaCoordinacionRepository extends JpaRepository<NotaCoordinacion, Integer> {
    List<NotaCoordinacion> findByEstudianteIdOrderByFechaCreacionDesc(Integer estudianteId);
    List<NotaCoordinacion> findByPracticaId(Integer practicaId);
    List<NotaCoordinacion> findByVinculacionId(Integer vinculacionId);
}
