package ec.edu.unibe.sistema_practicas.auditoria;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuditoriaEmitter {

    private final AuditoriaRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Auditoria registrar(String tabla, String accion, Usuario usuario, Object antes, Object despues) {
        if (tabla == null || tabla.isBlank() || accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("La auditoría requiere tabla y acción.");
        }
        if (tabla.trim().length() > 100 || accion.trim().length() > 50) {
            throw new IllegalArgumentException("La tabla o acción de auditoría supera la longitud permitida.");
        }
        Auditoria auditoria = new Auditoria();
        auditoria.setTablaAfectada(tabla.trim().toUpperCase(Locale.ROOT));
        auditoria.setAccion(accion.trim().toUpperCase(Locale.ROOT));
        auditoria.setDatosAntes(antes == null ? null : objectMapper.writeValueAsString(antes));
        auditoria.setDatosDespues(despues == null ? null : objectMapper.writeValueAsString(despues));
        auditoria.setUsuario(usuario);
        auditoria.setFecha(LocalDateTime.now());
        return auditoriaRepository.save(auditoria);
    }
}
