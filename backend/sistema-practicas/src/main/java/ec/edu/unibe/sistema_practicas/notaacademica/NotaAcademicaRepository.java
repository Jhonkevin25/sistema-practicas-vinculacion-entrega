package ec.edu.unibe.sistema_practicas.notaacademica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotaAcademicaRepository extends JpaRepository<NotaAcademica, Integer> {
    List<NotaAcademica> findByEstudianteId(Integer estudianteId);
    List<NotaAcademica> findByEstudianteIdAndEstado(Integer estudianteId, String estado);
    Optional<NotaAcademica> findFirstByEstudianteIdAndSemestreAndEstadoOrderByPeriodoAcademicoDesc(
            Integer estudianteId,
            Integer semestre,
            String estado);
    Optional<NotaAcademica> findByEstudianteIdAndPeriodoAcademicoAndSemestre(
            Integer estudianteId,
            String periodoAcademico,
            Integer semestre);
}
