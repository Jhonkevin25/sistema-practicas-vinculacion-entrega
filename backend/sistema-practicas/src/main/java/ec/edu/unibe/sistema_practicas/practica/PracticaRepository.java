package ec.edu.unibe.sistema_practicas.practica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PracticaRepository extends JpaRepository<Practica, Integer>, JpaSpecificationExecutor<Practica> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Practica p where p.id = :id")
    Optional<Practica> findByIdForUpdate(@Param("id") Integer id);
    List<Practica> findByEstudianteCarreraIn(Collection<String> carreras);
    List<Practica> findByEstudianteId(Integer estudianteId);
    List<Practica> findByEstudianteIdAndEmpresaIdAndPeriodoAcademico(Integer estudianteId, Integer empresaId,
                                                                       String periodoAcademico);
    List<Practica> findByTutorId(Integer tutorId);
    List<Practica> findByEncargadoId(Integer encargadoId);
    List<Practica> findByEmpresaIdAndPeriodoAcademico(Integer empresaId, String periodoAcademico);
    boolean existsByEstudianteIdAndTutorId(Integer estudianteId, Integer tutorId);
    long countByPeriodoAcademicoAndEstadoIn(String periodoAcademico, Collection<String> estados);
    List<Practica> findByEstadoIn(Collection<String> estados);
}
