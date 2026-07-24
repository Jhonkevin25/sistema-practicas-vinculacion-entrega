package ec.edu.unibe.sistema_practicas.vacante;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VacantePracticaRepository extends JpaRepository<VacantePractica, Integer> {
    List<VacantePractica> findByCarreraIn(Collection<String> carreras);
    List<VacantePractica> findByPeriodoAcademico(String periodoAcademico);
    List<VacantePractica> findByCarreraInAndPeriodoAcademico(
            Collection<String> carreras, String periodoAcademico);
    List<VacantePractica> findByEmpresaIdAndPeriodoAcademico(
            Integer empresaId, String periodoAcademico);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VacantePractica v where v.id = :id")
    Optional<VacantePractica> findByIdForUpdate(@Param("id") Integer id);
}
