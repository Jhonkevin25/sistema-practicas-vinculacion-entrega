package ec.edu.unibe.sistema_practicas.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findByTablaAfectadaOrderByFechaDesc(String tablaAfectada);
    List<Auditoria> findByTablaAfectadaOrderByFechaAsc(String tablaAfectada);
}
