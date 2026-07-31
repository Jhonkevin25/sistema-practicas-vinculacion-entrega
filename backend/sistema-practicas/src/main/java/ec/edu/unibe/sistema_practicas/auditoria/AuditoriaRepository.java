package ec.edu.unibe.sistema_practicas.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findByTablaAfectadaOrderByFechaDesc(String tablaAfectada);
    List<Auditoria> findByTablaAfectadaOrderByFechaAsc(String tablaAfectada);

    // Perf: la linea de tiempo de un expediente necesita solo las auditorias
    // de ESA practica/vinculacion, no un escaneo completo de la tabla filtrado
    // en Java. Se filtra en SQL sobre el jsonb (datos_antes/datos_despues)
    // con el operador ->> de Postgres.
    @Query(value = "SELECT * FROM auditoria WHERE tabla_afectada = :tabla "
            + "AND ((datos_antes->>'practicaId')::integer = :practicaId "
            + "OR (datos_despues->>'practicaId')::integer = :practicaId) "
            + "ORDER BY fecha ASC", nativeQuery = true)
    List<Auditoria> findByTablaAfectadaYPracticaIdOrderByFechaAsc(@Param("tabla") String tabla,
                                                                   @Param("practicaId") Integer practicaId);

    @Query(value = "SELECT * FROM auditoria WHERE tabla_afectada = :tabla "
            + "AND ((datos_antes->>'vinculacionId')::integer = :vinculacionId "
            + "OR (datos_despues->>'vinculacionId')::integer = :vinculacionId) "
            + "ORDER BY fecha ASC", nativeQuery = true)
    List<Auditoria> findByTablaAfectadaYVinculacionIdOrderByFechaAsc(@Param("tabla") String tabla,
                                                                      @Param("vinculacionId") Integer vinculacionId);
}
