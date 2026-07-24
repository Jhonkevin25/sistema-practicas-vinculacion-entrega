package ec.edu.unibe.sistema_practicas.bitacora;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface BitacoraRepository extends JpaRepository<Bitacora, Integer> {
    java.util.List<Bitacora> findByEstudianteId(Integer estudianteId);
    java.util.List<Bitacora> findByEstudianteCarreraIn(Collection<String> carreras);
    java.util.List<Bitacora> findByPracticaId(Integer practicaId);
    java.util.List<Bitacora> findByPracticaTutorId(Integer tutorId);
    java.util.List<Bitacora> findByVinculacionId(Integer vinculacionId);
    java.util.List<Bitacora> findByVinculacionTutorId(Integer tutorId);
    boolean existsByPracticaIdAndParcialAndEstadoIn(Integer practicaId, Integer parcial, Collection<String> estados);
    boolean existsByVinculacionIdAndParcialAndEstadoIn(Integer vinculacionId, Integer parcial, Collection<String> estados);

    @Query("select coalesce(sum(b.horas), 0) from Bitacora b where b.practica.id = :practicaId and b.estado = 'aprobada'")
    Integer sumHorasAprobadasByPracticaId(@Param("practicaId") Integer practicaId);

    @Query("select coalesce(sum(b.horas), 0) from Bitacora b where b.vinculacion.id = :vinculacionId and b.estado = 'aprobada'")
    Integer sumHorasAprobadasByVinculacionId(@Param("vinculacionId") Integer vinculacionId);
}
