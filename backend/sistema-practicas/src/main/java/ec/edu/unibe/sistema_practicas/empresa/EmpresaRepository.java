package ec.edu.unibe.sistema_practicas.empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer>, JpaSpecificationExecutor<Empresa> {
    Optional<Empresa> findByRuc(String ruc);
}
