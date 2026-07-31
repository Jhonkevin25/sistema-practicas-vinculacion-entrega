package ec.edu.unibe.sistema_practicas.asistencia;

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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/asistencias")
@RequiredArgsConstructor
public class AsistenciaController {

    private static final java.time.ZoneId ZONA_ECUADOR = java.time.ZoneId.of("America/Guayaquil");

    private final AsistenciaRepository asistenciaRepository;
    private final EstudianteRepository estudianteRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;

    @GetMapping
    public List<Asistencia> getAll(Authentication authentication,
                                   @AuthenticationPrincipal Usuario usuario) {
        return asistenciasVisibles(authentication, usuario);
    }

    @GetMapping("/me")
    public List<Asistencia> getMisAsistencias(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> asistenciaRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<Asistencia> getByEstudiante(@PathVariable Integer estudianteId,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario) {
        return asistenciasVisibles(authentication, usuario).stream()
                .filter(a -> a.getEstudiante() != null
                        && estudianteId.equals(a.getEstudiante().getId()))
                .toList();
    }

    @GetMapping("/practica/{practicaId}")
    public List<Asistencia> getByPractica(@PathVariable Integer practicaId,
                                         Authentication authentication,
                                         @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(practicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVer(practica, authentication, usuario);
        return asistenciaRepository.findByPracticaId(practicaId);
    }

    @GetMapping("/vinculacion/{vinculacionId}")
    public List<Asistencia> getByVinculacion(@PathVariable Integer vinculacionId,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(vinculacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVer(vinculacion, authentication, usuario);
        return asistenciaRepository.findByVinculacionId(vinculacionId);
    }

    @PostMapping
    @Transactional
    public Asistencia create(@Valid @RequestBody Asistencia asistencia,
                             Authentication authentication,
                             @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El registro de asistencia corresponde al tutor o a gestión académica.");
        }
        boolean tienePractica = asistencia.getPractica() != null && asistencia.getPractica().getId() != null;
        boolean tieneVinculacion = asistencia.getVinculacion() != null
                && asistencia.getVinculacion().getId() != null;
        if (tienePractica == tieneVinculacion) {
            throw new IllegalArgumentException(
                    "La asistencia debe indicar exactamente una práctica o vinculación.");
        }
        if (asistencia.getEstado() == null
                || !Set.of("Presente", "Atraso", "Falta").contains(asistencia.getEstado())) {
            throw new IllegalArgumentException("El estado de asistencia no es válido.");
        }
        if (asistencia.getFecha().isAfter(LocalDate.now(ZONA_ECUADOR))) {
            throw new IllegalArgumentException("No se puede registrar una asistencia con fecha futura.");
        }

        if (tienePractica) {
            Practica practica = practicaRepository.findById(asistencia.getPractica().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            verificarPuedeGestionar(practica, authentication, usuario);
            validarExpedienteAbierto(practica.getEstado(), practica.getCerradoEn());
            validarFecha(asistencia.getFecha(), practica.getFechaInicio(), practica.getFechaFin());
            if (asistenciaRepository.existsByPracticaIdAndFecha(practica.getId(), asistencia.getFecha())) {
                throw new IllegalArgumentException("Ya existe una asistencia para esta práctica en la fecha indicada.");
            }
            asistencia.setPractica(practica);
            asistencia.setVinculacion(null);
            asistencia.setEstudiante(practica.getEstudiante());
        } else {
            Vinculacion vinculacion = vinculacionRepository.findById(asistencia.getVinculacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            verificarPuedeGestionar(vinculacion, authentication, usuario);
            validarExpedienteAbierto(vinculacion.getEstado(), vinculacion.getCerradoEn());
            validarFecha(asistencia.getFecha(), vinculacion.getFechaInicio(), vinculacion.getFechaFin());
            if (asistenciaRepository.existsByVinculacionIdAndFecha(vinculacion.getId(), asistencia.getFecha())) {
                throw new IllegalArgumentException("Ya existe una asistencia para esta vinculación en la fecha indicada.");
            }
            asistencia.setPractica(null);
            asistencia.setVinculacion(vinculacion);
            asistencia.setEstudiante(vinculacion.getEstudiante());
        }
        Asistencia guardada = asistenciaRepository.save(asistencia);
        notificarAtrasoOFalta(guardada);
        return guardada;
    }

    private void notificarAtrasoOFalta(Asistencia asistencia) {
        if (!"Atraso".equals(asistencia.getEstado()) && !"Falta".equals(asistencia.getEstado())) return;
        String motivo = "Atraso".equals(asistencia.getEstado()) ? "un atraso" : "una falta";
        String ruta = asistencia.getVinculacion() != null ? "/dashboard/vinculacion" : "/dashboard/practicas";
        notificacionEmitter.emitir(
                asistencia.getEstudiante() == null ? null : asistencia.getEstudiante().getUsuario(),
                "asistencia_" + asistencia.getEstado().toLowerCase(),
                "Registro de asistencia: " + asistencia.getEstado(),
                "Tu tutor registró " + motivo + " el " + asistencia.getFecha()
                        + ". Recuerda asistir puntualmente a tu empresa/proyecto asignado.",
                "asistencia", asistencia.getId().longValue(),
                ruta);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private List<Asistencia> asistenciasDelTutor(Integer tutorId) {
        List<Asistencia> asistencias = new ArrayList<>();
        asistencias.addAll(asistenciaRepository.findByPracticaTutorId(tutorId));
        asistencias.addAll(asistenciaRepository.findByVinculacionTutorId(tutorId));
        return sinDuplicados(asistencias);
    }

    private List<Asistencia> asistenciasVisibles(Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return asistenciaRepository.findAll();
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> asistenciaRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (hasRole(authentication, "TUTOR")) {
            return usuario == null ? List.of() : asistenciasDelTutor(usuario.getId());
        }
        if (hasRole(authentication, "COORDINADOR")) {
            Optional<Set<String>> alcance = alcanceCoordinador.carrerasVisibles(authentication);
            if (alcance.isEmpty() || alcance.get().isEmpty()) return List.of();
            List<Asistencia> asistencias = new ArrayList<>();
            if (alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")) {
                asistencias.addAll(asistenciaRepository.findByPracticaEstudianteCarreraIn(alcance.get()));
            }
            if (alcanceCoordinador.procesoVisible(authentication, "VINCULACION")) {
                asistencias.addAll(asistenciaRepository.findByVinculacionEstudianteCarreraIn(alcance.get()));
            }
            return sinDuplicados(asistencias);
        }
        return List.of();
    }

    private void verificarPuedeVer(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")
                && carreraVisible(authentication, practica.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> actual = estudianteActual(usuario);
            if (actual.isPresent() && practica.getEstudiante() != null
                    && actual.get().getId().equals(practica.getEstudiante().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las asistencias de esta práctica.");
    }

    private void verificarPuedeVer(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "VINCULACION")
                && carreraVisible(authentication, vinculacion.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> actual = estudianteActual(usuario);
            if (actual.isPresent() && vinculacion.getEstudiante() != null
                    && actual.get().getId().equals(vinculacion.getEstudiante().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las asistencias de esta vinculación.");
    }

    private void verificarPuedeGestionar(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")
                && carreraVisible(authentication, practica.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR") && usuario != null && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No puedes registrar asistencias para esta práctica.");
    }

    private void verificarPuedeGestionar(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "VINCULACION")
                && carreraVisible(authentication, vinculacion.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR") && usuario != null && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No puedes registrar asistencias para esta vinculación.");
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private void validarExpedienteAbierto(String estado, Object cerradoEn) {
        if (cerradoEn != null || estado == null
                || Set.of("completado", "reprobado", "retirado").contains(estado.toLowerCase())) {
            throw new IllegalArgumentException("No se pueden registrar asistencias en un expediente finalizado.");
        }
    }

    private void validarFecha(LocalDate fecha, LocalDate inicio, LocalDate fin) {
        if (inicio != null && fecha.isBefore(inicio)) {
            throw new IllegalArgumentException("La asistencia no puede ser anterior al inicio del expediente.");
        }
        if (fin != null && fecha.isAfter(fin)) {
            throw new IllegalArgumentException("La asistencia no puede ser posterior al fin del expediente.");
        }
    }

    private List<Asistencia> sinDuplicados(List<Asistencia> asistencias) {
        Map<Integer, Asistencia> porId = new LinkedHashMap<>();
        asistencias.forEach(a -> porId.put(a.getId(), a));
        return List.copyOf(porId.values());
    }
}
