package ec.edu.unibe.sistema_practicas.vinculacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VinculacionRepository extends JpaRepository<Vinculacion, Integer>, JpaSpecificationExecutor<Vinculacion> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vinculacion v where v.id = :id")
    Optional<Vinculacion> findByIdForUpdate(@Param("id") Integer id);
    List<Vinculacion> findByEstudianteCarreraIn(Collection<String> carreras);
    List<Vinculacion> findByEstudianteId(Integer estudianteId);
    List<Vinculacion> findByTutorId(Integer tutorId);
    List<Vinculacion> findByEncargadoId(Integer encargadoId);
    List<Vinculacion> findByFundacionIdAndPeriodoAcademico(Integer fundacionId, String periodoAcademico);
    List<Vinculacion> findByProyectoId(Integer proyectoId);
    boolean existsByEstudianteIdAndTutorId(Integer estudianteId, Integer tutorId);
    long countByPeriodoAcademicoAndEstadoIn(String periodoAcademico, Collection<String> estados);
    List<Vinculacion> findByEstadoIn(Collection<String> estados);
}
