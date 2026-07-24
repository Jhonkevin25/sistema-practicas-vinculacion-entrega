package ec.edu.unibe.sistema_practicas.usuario;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> findByEmailIgnoreCaseForUpdate(@Param("email") String email);
    Optional<Usuario> findByExternalIdIgnoreCase(String externalId);
    Optional<Usuario> findByCedula(String cedula);
    List<Usuario> findDistinctByRoles_CodigoAndActivoTrueOrderByApellidoAscNombreAsc(String codigo);
    long countDistinctByActivoTrueAndRoles_Codigo(String codigo);
}
