package ec.edu.unibe.sistema_practicas.configuracion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FechasConvocatoriaRepository extends JpaRepository<FechasConvocatoria, Integer> {
    Optional<FechasConvocatoria> findByPeriodoAcademicoAndTipo(String periodoAcademico, String tipo);
    List<FechasConvocatoria> findByPeriodoAcademico(String periodoAcademico);
    List<FechasConvocatoria> findByTipo(String tipo);
}
