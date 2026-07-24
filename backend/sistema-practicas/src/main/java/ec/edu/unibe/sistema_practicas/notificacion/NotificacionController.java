package ec.edu.unibe.sistema_practicas.notificacion;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionRepository notificacionRepository;

    // La bandeja es personal: siempre se resuelve desde el JWT, nunca desde
    // un id enviado por el cliente
    @GetMapping("/me")
    public List<Notificacion> getMisNotificaciones(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null || usuario.getId() == null) return List.of();
        return notificacionRepository.findByUsuarioDestinoIdOrderByFechaCreacionDesc(usuario.getId());
    }

    @GetMapping("/me/no-leidas/count")
    public Map<String, Long> getConteoNoLeidas(@AuthenticationPrincipal Usuario usuario) {
        long count = usuario == null || usuario.getId() == null
                ? 0L
                : notificacionRepository.countByUsuarioDestinoIdAndLeidaFalse(usuario.getId());
        return Map.of("count", count);
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<Notificacion> marcarLeida(@PathVariable Integer id,
                                                    @AuthenticationPrincipal Usuario usuario) {
        return notificacionRepository.findById(id)
                .map(notificacion -> {
                    verificarEsDestinatario(notificacion, usuario);
                    notificacion.setLeida(true);
                    return ResponseEntity.ok(notificacionRepository.save(notificacion));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/leidas")
    public Map<String, Long> marcarTodasLeidas(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null || usuario.getId() == null) return Map.of("count", 0L);
        List<Notificacion> pendientes = notificacionRepository.findByUsuarioDestinoIdAndLeidaFalse(usuario.getId());
        pendientes.forEach(notificacion -> notificacion.setLeida(true));
        notificacionRepository.saveAll(pendientes);
        return Map.of("count", (long) pendientes.size());
    }

    private void verificarEsDestinatario(Notificacion notificacion, Usuario usuario) {
        if (usuario != null && usuario.getId() != null
                && notificacion.getUsuarioDestino() != null
                && usuario.getId().equals(notificacion.getUsuarioDestino().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes marcar notificaciones ajenas.");
    }
}
