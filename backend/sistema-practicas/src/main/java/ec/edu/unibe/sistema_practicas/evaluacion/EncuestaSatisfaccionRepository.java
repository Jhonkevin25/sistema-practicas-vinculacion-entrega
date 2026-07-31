package ec.edu.unibe.sistema_practicas.evaluacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EncuestaSatisfaccionRepository extends JpaRepository<EncuestaSatisfaccion, Integer> {
    Optional<EncuestaSatisfaccion> findByPracticaIdAndParcial(Integer practicaId, Integer parcial);
    boolean existsByPracticaIdAndParcial(Integer practicaId, Integer parcial);
    Optional<EncuestaSatisfaccion> findByVinculacionIdAndParcial(Integer vinculacionId, Integer parcial);
    boolean existsByVinculacionIdAndParcial(Integer vinculacionId, Integer parcial);
    List<EncuestaSatisfaccion> findByPracticaId(Integer practicaId);
    List<EncuestaSatisfaccion> findByVinculacionId(Integer vinculacionId);
    List<EncuestaSatisfaccion> findByEstudianteId(Integer estudianteId);
    // Batch fetch usado por el seguimiento: una sola consulta con IN en vez
    // de una por expediente.
    List<EncuestaSatisfaccion> findByPracticaIdIn(Collection<Integer> practicaIds);
    List<EncuestaSatisfaccion> findByVinculacionIdIn(Collection<Integer> vinculacionIds);
}
