package ec.edu.unibe.sistema_practicas.periodo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodoAcademicoRepository extends JpaRepository<PeriodoAcademico, Integer> {
    Optional<PeriodoAcademico> findByCodigo(String codigo);
    Optional<PeriodoAcademico> findByEstado(String estado);
    List<PeriodoAcademico> findAllByOrderByFechaInicioDescIdDesc();
}
