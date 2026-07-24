package ec.edu.unibe.sistema_practicas.evaluacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluacionPracticaDetalleRepository extends JpaRepository<EvaluacionPracticaDetalle, Integer> {
    Optional<EvaluacionPracticaDetalle> findByPracticaIdAndParcial(Integer practicaId, Integer parcial);
    Optional<EvaluacionPracticaDetalle> findByVinculacionIdAndParcial(Integer vinculacionId, Integer parcial);
    List<EvaluacionPracticaDetalle> findByPracticaId(Integer practicaId);
    List<EvaluacionPracticaDetalle> findByPracticaEstudianteId(Integer estudianteId);
    List<EvaluacionPracticaDetalle> findByPracticaEstudianteCarreraIn(Collection<String> carreras);
    List<EvaluacionPracticaDetalle> findByPracticaTutorId(Integer tutorId);
    List<EvaluacionPracticaDetalle> findByVinculacionId(Integer vinculacionId);
    List<EvaluacionPracticaDetalle> findByVinculacionEstudianteId(Integer estudianteId);
    List<EvaluacionPracticaDetalle> findByVinculacionEstudianteCarreraIn(Collection<String> carreras);
    List<EvaluacionPracticaDetalle> findByVinculacionTutorId(Integer tutorId);
}
