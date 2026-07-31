package ec.edu.unibe.sistema_practicas.asistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDate;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Integer> {
    List<Asistencia> findByEstudianteId(Integer estudianteId);
    List<Asistencia> findByPracticaId(Integer practicaId);
    List<Asistencia> findByVinculacionId(Integer vinculacionId);
    // Batch fetch usado por el seguimiento: una sola consulta con IN en vez
    // de una por expediente.
    List<Asistencia> findByPracticaIdIn(Collection<Integer> practicaIds);
    List<Asistencia> findByVinculacionIdIn(Collection<Integer> vinculacionIds);
    List<Asistencia> findByPracticaTutorId(Integer tutorId);
    List<Asistencia> findByVinculacionTutorId(Integer tutorId);
    List<Asistencia> findByPracticaEstudianteCarreraIn(Collection<String> carreras);
    List<Asistencia> findByVinculacionEstudianteCarreraIn(Collection<String> carreras);
    boolean existsByPracticaIdAndFecha(Integer practicaId, LocalDate fecha);
    boolean existsByVinculacionIdAndFecha(Integer vinculacionId, LocalDate fecha);
    java.util.Optional<Asistencia> findByPracticaIdAndFecha(Integer practicaId, LocalDate fecha);
    java.util.Optional<Asistencia> findByVinculacionIdAndFecha(Integer vinculacionId, LocalDate fecha);
}
