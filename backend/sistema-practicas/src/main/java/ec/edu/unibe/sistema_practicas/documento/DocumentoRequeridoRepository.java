package ec.edu.unibe.sistema_practicas.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRequeridoRepository extends JpaRepository<DocumentoRequerido, Integer> {
    List<DocumentoRequerido> findByActivoTrue();
    List<DocumentoRequerido> findByProcesoAndActivoTrue(String proceso);
}
