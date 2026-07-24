package ec.edu.unibe.sistema_practicas.importacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

    @Query("SELECT i FROM Importacion i WHERE " +
           "(cast(:tipo as text) IS NULL OR i.tipo = :tipo) AND " +
           "(cast(:estado as text) IS NULL OR i.estado = :estado)")
    Page<Importacion> findByFiltros(@Param("tipo") String tipo, @Param("estado") String estado, Pageable pageable);
}
