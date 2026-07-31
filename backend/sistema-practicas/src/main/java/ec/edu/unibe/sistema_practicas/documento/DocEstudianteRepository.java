package ec.edu.unibe.sistema_practicas.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocEstudianteRepository extends JpaRepository<DocEstudiante, Integer> {
    List<DocEstudiante> findByEstudianteId(Integer estudianteId);
    List<DocEstudiante> findByEstudianteIdAndProceso(Integer estudianteId, String proceso);
    Optional<DocEstudiante> findByEstudianteIdAndTipoDocumento(Integer estudianteId, String tipoDocumento);
    Optional<DocEstudiante> findByEstudianteIdAndTipoDocumentoAndProceso(Integer estudianteId, String tipoDocumento, String proceso);

    // Compatibilidad retroactiva: un documento se considera vigente para la
    // etapa actual (PRACTICA_1 / PRACTICA_2) si coincide con esa etapa, o si es
    // una fila "legacy" cargada antes de que existiera la distincion por etapa
    // (etapa = NULL). Esas filas legacy no obligan a los estudiantes que ya
    // estaban en Practica II a resubir documentos ya aprobados.
    @Query("select (count(d) > 0) from DocEstudiante d where d.estudiante.id = :estudianteId "
            + "and d.tipoDocumento = :tipoDocumento and d.proceso = :proceso "
            + "and (d.etapa = :etapa or d.etapa is null) and d.estado = :estado")
    boolean existsVigentePorEtapa(@Param("estudianteId") Integer estudianteId,
                                   @Param("tipoDocumento") String tipoDocumento,
                                   @Param("proceso") String proceso,
                                   @Param("etapa") String etapa,
                                   @Param("estado") String estado);
}
