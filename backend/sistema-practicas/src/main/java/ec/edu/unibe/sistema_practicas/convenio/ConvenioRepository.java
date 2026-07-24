package ec.edu.unibe.sistema_practicas.convenio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ConvenioRepository extends JpaRepository<Convenio, Integer> {

    List<Convenio> findByEmpresaIdOrderByFechaFinDesc(Integer empresaId);

    List<Convenio> findByFundacionIdOrderByFechaFinDesc(Integer fundacionId);

    List<Convenio> findByEmpresaIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Integer empresaId, String estado, LocalDate fechaInicio, LocalDate fechaFin);

    List<Convenio> findByFundacionIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Integer fundacionId, String estado, LocalDate fechaInicio, LocalDate fechaFin);

    List<Convenio> findByEmpresaIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            String estado, LocalDate fechaInicio, LocalDate fechaFin);

    List<Convenio> findByFundacionIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            String estado, LocalDate fechaInicio, LocalDate fechaFin);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Integer id);
}
