package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfertaCuposFundacionRepository extends JpaRepository<OfertaCuposFundacion, Integer> {
    List<OfertaCuposFundacion> findByPeriodoAcademico(String periodoAcademico);
    Optional<OfertaCuposFundacion> findByFundacionIdAndPeriodoAcademico(
            Integer fundacionId, String periodoAcademico);
    boolean existsByFundacionIdAndPeriodoAcademico(Integer fundacionId, String periodoAcademico);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OfertaCuposFundacion o where o.fundacion.id = :fundacionId "
            + "and o.periodoAcademico = :periodo")
    Optional<OfertaCuposFundacion> findByFundacionIdAndPeriodoAcademicoForUpdate(
            @Param("fundacionId") Integer fundacionId,
            @Param("periodo") String periodo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OfertaCuposFundacion o where o.id = :id")
    Optional<OfertaCuposFundacion> findByIdForUpdate(@Param("id") Integer id);
}
