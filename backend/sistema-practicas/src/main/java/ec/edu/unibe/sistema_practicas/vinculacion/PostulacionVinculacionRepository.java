package ec.edu.unibe.sistema_practicas.vinculacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostulacionVinculacionRepository extends JpaRepository<PostulacionVinculacion, Integer> {
    List<PostulacionVinculacion> findByEstudianteId(Integer estudianteId);
    List<PostulacionVinculacion> findByEstudianteCarreraIn(Collection<String> carreras);
    boolean existsByEstudianteIdAndEstado(Integer estudianteId, String estado);
    boolean existsByProyectoId(Integer proyectoId);
    List<PostulacionVinculacion> findByEstadoAndPeriodoAcademico(String estado, String periodoAcademico);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PostulacionVinculacion p where p.id = :id")
    Optional<PostulacionVinculacion> findByIdForUpdate(@Param("id") Integer id);
}
