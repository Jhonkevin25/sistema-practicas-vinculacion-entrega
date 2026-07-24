package ec.edu.unibe.sistema_practicas.notacoordinacion;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/notas-coordinacion")
@RequiredArgsConstructor
public class NotaCoordinacionController {

    private final NotaCoordinacionRepository notaCoordinacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;

    @GetMapping("/me")
    public List<NotaCoordinacion> getMisNotas(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> notaCoordinacionRepository.findByEstudianteIdOrderByFechaCreacionDesc(est.getId()))
                .orElse(List.of());
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<NotaCoordinacion> getByEstudiante(@PathVariable Integer estudianteId,
                                                  Authentication authentication,
                                                  @AuthenticationPrincipal Usuario usuario) {
        verificarPuedeVerEstudiante(estudianteId, authentication, usuario);
        return notaCoordinacionRepository.findByEstudianteIdOrderByFechaCreacionDesc(estudianteId);
    }

    @PostMapping
    @Transactional
    public NotaCoordinacion create(@Valid @RequestBody NotaCoordinacion nota,
                                   Authentication authentication,
                                   @AuthenticationPrincipal Usuario usuario) {
        if (nota.getEstudiante() == null || nota.getEstudiante().getId() == null) {
            throw new IllegalArgumentException("La nota debe estar asociada a un estudiante.");
        }
        Estudiante estudiante = estudianteRepository.findById(nota.getEstudiante().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        verificarPuedeCrear(estudiante, authentication);

        if (nota.getMensaje() == null || nota.getMensaje().isBlank()) {
            throw new IllegalArgumentException("La nota no puede estar vacía.");
        }

        boolean tienePractica = nota.getPractica() != null && nota.getPractica().getId() != null;
        boolean tieneVinculacion = nota.getVinculacion() != null && nota.getVinculacion().getId() != null;
        if (tienePractica == tieneVinculacion) {
            throw new IllegalArgumentException("La nota debe asociarse a exactamente una práctica o vinculación.");
        }

        Practica practica = null;
        Vinculacion vinculacion = null;
        if (tienePractica) {
            practica = practicaRepository.findById(nota.getPractica().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            if (practica.getEstudiante() == null || !estudiante.getId().equals(practica.getEstudiante().getId())) {
                throw new IllegalArgumentException("La práctica no pertenece al estudiante indicado.");
            }
        } else {
            vinculacion = vinculacionRepository.findById(nota.getVinculacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            if (vinculacion.getEstudiante() == null || !estudiante.getId().equals(vinculacion.getEstudiante().getId())) {
                throw new IllegalArgumentException("La vinculación no pertenece al estudiante indicado.");
            }
        }

        nota.setEstudiante(estudiante);
        nota.setPractica(practica);
        nota.setVinculacion(vinculacion);
        nota.setAutor(usuario);
        nota.setFechaCreacion(LocalDateTime.now());
        NotaCoordinacion guardada = notaCoordinacionRepository.save(nota);

        String ruta = tienePractica ? "/dashboard/practicas" : "/dashboard/vinculacion";
        notificacionEmitter.emitir(
                estudiante.getUsuario(),
                "nota_coordinacion",
                "Nueva nota de tu coordinador",
                "Tu coordinador registró una nota de seguimiento sobre tu expediente.",
                "nota_coordinacion", guardada.getId().longValue(),
                "/dashboard/expediente-academico");

        Usuario tutor = tienePractica ? practica.getTutor() : vinculacion.getTutor();
        if (tutor != null) {
            notificacionEmitter.emitir(
                    tutor,
                    "nota_coordinacion",
                    "Nota de coordinación sobre tu estudiante",
                    "El coordinador registró una nota de seguimiento sobre " + nombreEstudiante(estudiante) + ".",
                    "nota_coordinacion", guardada.getId().longValue(),
                    ruta);
        }

        return guardada;
    }

    private String nombreEstudiante(Estudiante estudiante) {
        Usuario usuario = estudiante.getUsuario();
        if (usuario == null) return "un estudiante";
        String nombre = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
        return nombre.isBlank() ? "un estudiante" : nombre;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private boolean tutorAsignado(Integer estudianteId, Integer tutorId) {
        return practicaRepository.existsByEstudianteIdAndTutorId(estudianteId, tutorId)
                || vinculacionRepository.existsByEstudianteIdAndTutorId(estudianteId, tutorId);
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private void verificarPuedeVerEstudiante(Integer estudianteId, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId).orElse(null);
        if (estudiante == null) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && tutorAsignado(estudianteId, usuario.getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> actual = estudianteActual(usuario);
            if (actual.isPresent() && estudianteId.equals(actual.get().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las notas de coordinación de este estudiante.");
    }

    private void verificarPuedeCrear(Estudiante estudiante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes registrar notas de coordinación para este estudiante.");
    }
}
