package ec.edu.unibe.sistema_practicas.auditoria;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaRepository auditoriaRepository;

    // Lista blanca de tablas consultables: la AUDITORIA tambien recibe filas de
    // triggers de BD (p. ej. USUARIOS, cuyo jsonb incluye password_hash) que no
    // deben exponerse por la API
    private static final Set<String> TABLAS_CONSULTABLES = Set.of(
            "POSTULACIONES_MERITOCRATICAS",
            "PRACTICAS",
            "VINCULACION",
            "DOCUMENTOS_REQUERIDOS",
            "EVALUACIONES_PRACTICAS_DETALLE",
            "NOTAS_ACADEMICAS",
            "IMPORTACIONES"
    );

    @GetMapping
    public List<AuditoriaResponse> getByTabla(@RequestParam String tabla) {
        String tablaFinal = tabla == null ? "" : tabla.trim().toUpperCase(Locale.ROOT);
        if (!TABLAS_CONSULTABLES.contains(tablaFinal)) {
            throw new IllegalArgumentException("La auditoría de esa tabla no es consultable por la API.");
        }
        return auditoriaRepository.findByTablaAfectadaOrderByFechaDesc(tablaFinal).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditoriaResponse toResponse(Auditoria auditoria) {
        Usuario usuario = auditoria.getUsuario();
        UsuarioAuditoriaResponse actor = usuario == null ? null
                : new UsuarioAuditoriaResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido());
        return new AuditoriaResponse(
                auditoria.getId(), auditoria.getTablaAfectada(), auditoria.getAccion(),
                auditoria.getDatosAntes(), auditoria.getDatosDespues(), actor, auditoria.getFecha());
    }

    public record UsuarioAuditoriaResponse(Integer id, String nombre, String apellido) {}

    public record AuditoriaResponse(
            Long id, String tablaAfectada, String accion, String datosAntes, String datosDespues,
            UsuarioAuditoriaResponse usuario, LocalDateTime fecha) {}
}
