package ec.edu.unibe.sistema_practicas.notificacion;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Emisor compartido de notificaciones internas (estilo AlcanceCoordinador):
// los controladores lo invocan en los eventos del sistema. Es tolerante: si
// el destinatario no se puede resolver o el INSERT falla, no interrumpe la
// operación de negocio que origina el evento (los controladores llaman al
// emitter después de persistir; un fallo aquí nunca debe volverse un 500).
@Component
@RequiredArgsConstructor
public class NotificacionEmitter {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEmitter.class);

    private final NotificacionRepository notificacionRepository;
    private final CorreoNotificacionComponent correoNotificacionComponent;

    public void emitir(Usuario destino, String tipo, String titulo, String mensaje,
                       String referenciaTipo, Long referenciaId, String link) {
        if (destino == null || destino.getId() == null) return;
        try {
            Notificacion notificacion = new Notificacion();
            notificacion.setUsuarioDestino(destino);
            notificacion.setTipo(tipo);
            notificacion.setTitulo(titulo);
            notificacion.setMensaje(mensaje);
            notificacion.setLeida(false);
            notificacion.setFechaCreacion(LocalDateTime.now());
            notificacion.setReferenciaTipo(referenciaTipo);
            notificacion.setReferenciaId(referenciaId);
            notificacion.setLink(link);
            Notificacion guardada = notificacionRepository.save(notificacion);
            try {
                correoNotificacionComponent.enviarSiAplica(guardada);
            } catch (Exception e) {
                log.warn("No se pudo programar el correo de la notificación tipo '{}' para el usuario {}: {}",
                        tipo, destino.getId(), e.getMessage());
            }
        } catch (Exception e) {
            log.warn("No se pudo emitir la notificación tipo '{}' para el usuario {}: {}",
                    tipo, destino.getId(), e.getMessage());
        }
    }
}
