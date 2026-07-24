package ec.edu.unibe.sistema_practicas.comentarioseguimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComentarioSeguimientoRepository extends JpaRepository<ComentarioSeguimiento, Integer> {
    List<ComentarioSeguimiento> findByPracticaIdOrderByFechaCreacionAsc(Integer practicaId);
    List<ComentarioSeguimiento> findByVinculacionIdOrderByFechaCreacionAsc(Integer vinculacionId);
}
