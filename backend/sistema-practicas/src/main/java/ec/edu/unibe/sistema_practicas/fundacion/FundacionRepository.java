package ec.edu.unibe.sistema_practicas.fundacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FundacionRepository extends JpaRepository<Fundacion, Integer>, JpaSpecificationExecutor<Fundacion> {
    Optional<Fundacion> findByRuc(String ruc);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fundacion f where f.id = :id")
    Optional<Fundacion> findByIdForUpdate(@Param("id") Integer id);
}
