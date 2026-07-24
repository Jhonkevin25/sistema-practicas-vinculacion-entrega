package ec.edu.unibe.sistema_practicas.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {
    List<Notificacion> findByUsuarioDestinoIdOrderByFechaCreacionDesc(Integer usuarioDestinoId);
    List<Notificacion> findByUsuarioDestinoIdAndLeidaFalse(Integer usuarioDestinoId);
    long countByUsuarioDestinoIdAndLeidaFalse(Integer usuarioDestinoId);
    boolean existsByReferenciaTipoAndReferenciaIdAndTipoAndFechaCreacionAfter(
            String referenciaTipo, Long referenciaId, String tipo, LocalDateTime desde);
}
