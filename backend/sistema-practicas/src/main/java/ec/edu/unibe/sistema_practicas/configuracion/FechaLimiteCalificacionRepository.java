package ec.edu.unibe.sistema_practicas.configuracion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FechaLimiteCalificacionRepository extends JpaRepository<FechaLimiteCalificacion, Integer> {
    Optional<FechaLimiteCalificacion> findByPeriodoAcademicoAndParcial(String periodoAcademico, Integer parcial);
    List<FechaLimiteCalificacion> findByPeriodoAcademico(String periodoAcademico);
}
