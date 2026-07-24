package ec.edu.unibe.sistema_practicas.notificacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CorreoColaRepository extends JpaRepository<CorreoCola, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorreoCola c where c.id = :id")
    Optional<CorreoCola> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c FROM CorreoCola c WHERE c.estado IN ('PENDIENTE', 'FALLIDO') AND c.intentos < :maxIntentos ORDER BY c.fechaCreacion ASC")
    List<CorreoCola> findPendientesYFallidosConLimites(@Param("maxIntentos") int maxIntentos, Pageable pageable);

    @Query("SELECT c FROM CorreoCola c WHERE " +
           "(cast(:estado as text) IS NULL OR c.estado = :estado) AND " +
           "(cast(:destinatario as text) IS NULL OR LOWER(c.destinatario) LIKE LOWER(CONCAT('%', cast(:destinatario as text), '%')))")
    Page<CorreoCola> findByFiltros(@Param("estado") String estado, @Param("destinatario") String destinatario, Pageable pageable);
}
