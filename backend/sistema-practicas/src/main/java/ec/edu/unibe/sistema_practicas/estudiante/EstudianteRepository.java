package ec.edu.unibe.sistema_practicas.estudiante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Integer>, JpaSpecificationExecutor<Estudiante> {
    List<Estudiante> findByCarreraIn(Collection<String> carreras);
    long countByCarreraIn(Collection<String> carreras);
    Optional<Estudiante> findByMatricula(String matricula);
    Optional<Estudiante> findByUsuarioEmail(String email);
    Optional<Estudiante> findByUsuarioId(Integer usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estudiante e where e.id = :id")
    Optional<Estudiante> findByIdForUpdate(@Param("id") Integer id);

    // Proyeccion minima para excluir del selector de "Nuevo estudiante" a los
    // usuarios que ya tienen expediente, sin cargar las entidades completas
    @Query("select e.usuario.id from Estudiante e")
    List<Integer> findUsuarioIdsConExpediente();
}
