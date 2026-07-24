package ec.edu.unibe.sistema_practicas.notaacademica;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/notas-academicas")
@RequiredArgsConstructor
public class NotaAcademicaController {

    private static final Set<String> FUENTES = Set.of("MANUAL", "CSV_UNIVERSIDAD", "API_UNIVERSIDAD");
    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "VERIFICADO", "RECHAZADO");

    private final NotaAcademicaRepository notaAcademicaRepository;
    private final EstudianteRepository estudianteRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;
    private final AuditoriaEmitter auditoriaEmitter;

    @GetMapping
    public List<NotaAcademica> getAll(Authentication authentication,
                                      @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> notaAcademicaRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        List<NotaAcademica> todas = notaAcademicaRepository.findAll();
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> todas.stream()
                        .filter(n -> n.getEstudiante() != null
                                && carreras.contains(n.getEstudiante().getCarrera()))
                        .toList())
                .orElse(todas);
    }

    @GetMapping("/me")
    public List<NotaAcademica> getMisNotas(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> notaAcademicaRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @PostMapping
    @Transactional
    public NotaAcademica create(@Valid @RequestBody NotaAcademica nota,
                                Authentication authentication,
                                @AuthenticationPrincipal Usuario usuario) {
        Estudiante estudiante;
        if (hasRole(authentication, "ESTUDIANTE")) {
            estudiante = estudianteActual(usuario)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tu usuario no tiene un expediente de estudiante asociado."));
        } else {
            if (nota.getEstudiante() == null || nota.getEstudiante().getId() == null) {
                throw new IllegalArgumentException("La nota académica debe estar asociada a un estudiante.");
            }
            estudiante = estudianteRepository.findById(nota.getEstudiante().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
            verificarPuedeGestionarEstudiante(estudiante, authentication);
        }

        validarNota(nota);
        nota.setEstudiante(estudiante);
        nota.setCarrera(estudiante.getCarrera());
        nota.setEmailInstitucional(estudiante.getUsuario() == null ? null : estudiante.getUsuario().getEmail());
        nota.setEstado("PENDIENTE");
        if (nota.getFuente() == null || nota.getFuente().isBlank()) {
            nota.setFuente("MANUAL");
        }
        nota.setVerificadoPor(null);
        nota.setFechaVerificacion(null);
        LocalDateTime ahora = LocalDateTime.now();
        nota.setCreatedAt(ahora);
        nota.setUpdatedAt(ahora);
        NotaAcademica guardada = notaAcademicaRepository.save(nota);
        auditoriaEmitter.registrar("NOTAS_ACADEMICAS", "CARGAR_NOTA_ACADEMICA", usuario,
                null, snapshotNota(guardada));
        return guardada;
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<NotaAcademica> verificar(@PathVariable Integer id,
                                                   @RequestBody NotaAcademica details,
                                                   Authentication authentication,
                                                   @AuthenticationPrincipal Usuario usuario) {
        return notaAcademicaRepository.findById(id)
                .map(nota -> {
                    Map<String, Object> antes = snapshotNota(nota);
                    verificarPuedeGestionarEstudiante(nota.getEstudiante(), authentication);
                    if (details.getEstado() == null || !ESTADOS.contains(details.getEstado())) {
                        throw new IllegalArgumentException("Estado de verificación inválido.");
                    }
                    if ("RECHAZADO".equals(details.getEstado())
                            && (details.getObservaciones() == null || details.getObservaciones().isBlank())) {
                        throw new IllegalArgumentException("El rechazo de una nota académica requiere observaciones.");
                    }
                    nota.setEstado(details.getEstado());
                    nota.setObservaciones(details.getObservaciones());
                    nota.setVerificadoPor(usuario);
                    nota.setFechaVerificacion(LocalDateTime.now());
                    nota.setUpdatedAt(LocalDateTime.now());
                    NotaAcademica guardada = notaAcademicaRepository.save(nota);
                    String accionAuditoria = switch (guardada.getEstado()) {
                        case "VERIFICADO" -> "VERIFICAR_NOTA_ACADEMICA";
                        case "RECHAZADO" -> "RECHAZAR_NOTA_ACADEMICA";
                        default -> "ACTUALIZAR_ESTADO_NOTA";
                    };
                    auditoriaEmitter.registrar("NOTAS_ACADEMICAS",
                            accionAuditoria, usuario, antes, snapshotNota(guardada));
                    if ("VERIFICADO".equals(guardada.getEstado()) || "RECHAZADO".equals(guardada.getEstado())) {
                        boolean verificada = "VERIFICADO".equals(guardada.getEstado());
                        notificacionEmitter.emitir(
                                guardada.getEstudiante() == null ? null : guardada.getEstudiante().getUsuario(),
                                "nota_registrada",
                                verificada ? "Nota académica verificada" : "Nota académica rechazada",
                                verificada
                                        ? "Tu nota académica del semestre " + guardada.getSemestre() + " fue verificada."
                                        : "Tu nota académica del semestre " + guardada.getSemestre()
                                                + " fue rechazada. Revisa las observaciones.",
                                "nota_academica", guardada.getId().longValue(), "/dashboard/practicas");
                    }
                    return ResponseEntity.ok(guardada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> snapshotNota(NotaAcademica nota) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", nota.getId());
        datos.put("estudianteId", nota.getEstudiante() == null ? null : nota.getEstudiante().getId());
        datos.put("carrera", nota.getCarrera());
        datos.put("semestre", nota.getSemestre());
        datos.put("periodoAcademico", nota.getPeriodoAcademico());
        datos.put("promedio", nota.getPromedio());
        datos.put("fuente", nota.getFuente());
        datos.put("estado", nota.getEstado());
        datos.put("observaciones", nota.getObservaciones());
        return datos;
    }

    private void validarNota(NotaAcademica nota) {
        if (nota.getSemestre() == null || nota.getSemestre() < 1) {
            throw new IllegalArgumentException("El semestre debe ser mayor o igual a 1.");
        }
        if (nota.getPeriodoAcademico() == null || nota.getPeriodoAcademico().isBlank()) {
            throw new IllegalArgumentException("El periodo académico es obligatorio.");
        }
        if (nota.getPromedio() == null || nota.getPromedio() < 0 || nota.getPromedio() > 10) {
            throw new IllegalArgumentException("El promedio académico debe estar entre 0 y 10.");
        }
        if (nota.getFuente() != null && !nota.getFuente().isBlank() && !FUENTES.contains(nota.getFuente())) {
            throw new IllegalArgumentException("Fuente de nota académica inválida.");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(true);
    }

    private void verificarPuedeGestionarEstudiante(Estudiante estudiante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar notas académicas de este estudiante.");
    }
}
