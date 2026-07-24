package ec.edu.unibe.sistema_practicas.postulacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostulacionMeritocraticaRepository extends JpaRepository<PostulacionMeritocratica, Integer> {
    List<PostulacionMeritocratica> findByEstudianteId(Integer estudianteId);
    List<PostulacionMeritocratica> findByEstudianteCarreraIn(Collection<String> carreras);
    List<PostulacionMeritocratica> findByEstado(String estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PostulacionMeritocratica p where p.id = :id")
    Optional<PostulacionMeritocratica> findByIdForUpdate(@Param("id") Integer id);
}
