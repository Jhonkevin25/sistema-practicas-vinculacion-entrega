package ec.edu.unibe.sistema_practicas.ofertacupo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfertaCuposEmpresaRepository extends JpaRepository<OfertaCuposEmpresa, Integer> {
    List<OfertaCuposEmpresa> findByPeriodoAcademico(String periodoAcademico);
    Optional<OfertaCuposEmpresa> findByEmpresaIdAndPeriodoAcademico(Integer empresaId, String periodoAcademico);
    boolean existsByEmpresaIdAndPeriodoAcademico(Integer empresaId, String periodoAcademico);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OfertaCuposEmpresa o where o.empresa.id = :empresaId and o.periodoAcademico = :periodo")
    Optional<OfertaCuposEmpresa> findByEmpresaIdAndPeriodoAcademicoForUpdate(
            @Param("empresaId") Integer empresaId,
            @Param("periodo") String periodo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OfertaCuposEmpresa o where o.id = :id")
    Optional<OfertaCuposEmpresa> findByIdForUpdate(@Param("id") Integer id);
}
