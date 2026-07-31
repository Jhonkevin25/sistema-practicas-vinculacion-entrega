package ec.edu.unibe.sistema_practicas.proyecto;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProyectoRepository extends JpaRepository<Proyecto, Integer> {
    List<Proyecto> findByFundacionId(Integer fundacionId);

    List<Proyecto> findByFundacionIdAndPeriodo(Integer fundacionId, String periodo);

    // Fase de optimización: variante batch para enriquecer varias ofertas de
    // cupos de fundación con una sola consulta (evita N+1 en el listado).
    List<Proyecto> findByFundacionIdInAndPeriodo(Collection<Integer> fundacionIds, String periodo);

    List<Proyecto> findByPeriodo(String periodo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proyecto p where p.id = :id")
    Optional<Proyecto> findByIdForUpdate(@Param("id") Integer id);
}
