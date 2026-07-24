package ec.edu.unibe.sistema_practicas.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocEstudianteRepository extends JpaRepository<DocEstudiante, Integer> {
    List<DocEstudiante> findByEstudianteId(Integer estudianteId);
    List<DocEstudiante> findByEstudianteIdAndProceso(Integer estudianteId, String proceso);
    Optional<DocEstudiante> findByEstudianteIdAndTipoDocumento(Integer estudianteId, String tipoDocumento);
    Optional<DocEstudiante> findByEstudianteIdAndTipoDocumentoAndProceso(Integer estudianteId, String tipoDocumento, String proceso);
}
