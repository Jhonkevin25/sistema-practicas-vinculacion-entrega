package ec.edu.unibe.sistema_practicas.comentarioseguimiento;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.coordinador.CoordinadorCarrera;
import ec.edu.unibe.sistema_practicas.coordinador.CoordinadorCarreraRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/comentarios-seguimiento")
@RequiredArgsConstructor
public class ComentarioSeguimientoController {

    private static final Set<String> AUDIENCIAS = Set.of("ESTUDIANTE", "TUTOR", "COORDINACION", "TODOS");
    private static final Set<String> ESTADOS_TERMINALES = Set.of("completado", "reprobado", "retirado");

    private final ComentarioSeguimientoRepository comentarioRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final CoordinadorCarreraRepository coordinadorRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;

    @GetMapping("/practica/{practicaId}")
    public List<ComentarioSeguimientoResponse> getByPractica(@PathVariable Integer practicaId,
                                                             Authentication authentication,
                                                             @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(practicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVer(practica, authentication, usuario);
        return visibles(comentarioRepository.findByPracticaIdOrderByFechaCreacionAsc(practicaId),
                authentication, usuario);
    }

    @GetMapping("/vinculacion/{vinculacionId}")
    public List<ComentarioSeguimientoResponse> getByVinculacion(@PathVariable Integer vinculacionId,
                                                                Authentication authentication,
                                                                @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(vinculacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVer(vinculacion, authentication, usuario);
        return visibles(comentarioRepository.findByVinculacionIdOrderByFechaCreacionAsc(vinculacionId),
                authentication, usuario);
    }

    @GetMapping("/me")
    public List<ComentarioSeguimientoResponse> getMisComentarios(Authentication authentication,
                                                                  @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null || (!hasRole(authentication, "ESTUDIANTE") && !hasRole(authentication, "TUTOR"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta consulta personal corresponde al estudiante o al tutor.");
        }
        List<ComentarioSeguimiento> comentarios = new ArrayList<>();
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = estudianteRepository.findByUsuarioId(usuario.getId());
            if (estudiante.isEmpty()) return List.of();
            practicaRepository.findByEstudianteId(estudiante.get().getId()).forEach(practica ->
                    comentarios.addAll(comentarioRepository.findByPracticaIdOrderByFechaCreacionAsc(practica.getId())));
            vinculacionRepository.findByEstudianteId(estudiante.get().getId()).forEach(vinculacion ->
                    comentarios.addAll(comentarioRepository.findByVinculacionIdOrderByFechaCreacionAsc(vinculacion.getId())));
        } else {
            practicaRepository.findByTutorId(usuario.getId()).forEach(practica ->
                    comentarios.addAll(comentarioRepository.findByPracticaIdOrderByFechaCreacionAsc(practica.getId())));
            vinculacionRepository.findByTutorId(usuario.getId()).forEach(vinculacion ->
                    comentarios.addAll(comentarioRepository.findByVinculacionIdOrderByFechaCreacionAsc(vinculacion.getId())));
        }
        return visibles(comentarios.stream().distinct().toList(), authentication, usuario);
    }

    @PostMapping
    @Transactional
    public ComentarioSeguimientoResponse create(@RequestBody ComentarioSeguimientoRequest request,
                                                 Authentication authentication,
                                                 @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida.");
        }
        boolean tienePractica = request.practicaId() != null;
        boolean tieneVinculacion = request.vinculacionId() != null;
        if (tienePractica == tieneVinculacion) {
            throw new IllegalArgumentException("El comentario debe indicar exactamente una práctica o vinculación.");
        }
        String audiencia = normalizar(request.audiencia());
        String mensaje = request.mensaje() == null ? "" : request.mensaje().trim();
        if (!AUDIENCIAS.contains(audiencia)) {
            throw new IllegalArgumentException("La audiencia del comentario no es válida.");
        }
        if (mensaje.length() < 5 || mensaje.length() > 2000) {
            throw new IllegalArgumentException("El comentario debe tener entre 5 y 2000 caracteres.");
        }
        validarAudienciaDelRol(audiencia, authentication);

        Practica practica = null;
        Vinculacion vinculacion = null;
        Estudiante estudiante;
        Usuario tutor;
        String proceso;
        if (tienePractica) {
            practica = practicaRepository.findById(request.practicaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            verificarPuedeVer(practica, authentication, usuario);
            validarExpedienteActivo(practica.getEstado(), practica.getCerradoEn());
            estudiante = practica.getEstudiante();
            tutor = practica.getTutor();
            proceso = "PRACTICAS";
        } else {
            vinculacion = vinculacionRepository.findById(request.vinculacionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            verificarPuedeVer(vinculacion, authentication, usuario);
            validarExpedienteActivo(vinculacion.getEstado(), vinculacion.getCerradoEn());
            estudiante = vinculacion.getEstudiante();
            tutor = vinculacion.getTutor();
            proceso = "VINCULACION";
        }

        List<Usuario> destinos = destinatarios(audiencia, estudiante, tutor, proceso, usuario);
        if (destinos.isEmpty()) {
            throw new IllegalArgumentException("No existe un destinatario activo para este comentario.");
        }

        ComentarioSeguimiento comentario = new ComentarioSeguimiento();
        comentario.setPractica(practica);
        comentario.setVinculacion(vinculacion);
        comentario.setAutor(usuario);
        comentario.setAudiencia(audiencia);
        comentario.setMensaje(mensaje);
        comentario.setFechaCreacion(LocalDateTime.now());
        ComentarioSeguimiento guardado = comentarioRepository.save(comentario);

        String procesoTexto = "PRACTICAS".equals(proceso) ? "práctica" : "vinculación";
        String link = "PRACTICAS".equals(proceso) ? "/dashboard/practicas" : "/dashboard/vinculacion";
        String resumen = mensaje.length() > 220 ? mensaje.substring(0, 217) + "..." : mensaje;
        for (Usuario destino : destinos) {
            notificacionEmitter.emitir(destino, "nota_coordinacion",
                    "Nuevo comentario de seguimiento",
                    nombre(usuario) + " registró un comentario en tu expediente de " + procesoTexto + ": " + resumen,
                    "comentario_seguimiento", guardado.getId().longValue(), link);
        }
        return respuesta(guardado);
    }

    private List<ComentarioSeguimientoResponse> visibles(List<ComentarioSeguimiento> comentarios,
                                                          Authentication authentication,
                                                          Usuario usuario) {
        return comentarios.stream()
                .filter(comentario -> puedeLeerComentario(comentario, authentication, usuario))
                .sorted(java.util.Comparator.comparing(ComentarioSeguimiento::getFechaCreacion))
                .map(this::respuesta)
                .toList();
    }

    private boolean puedeLeerComentario(ComentarioSeguimiento comentario, Authentication authentication,
                                         Usuario usuario) {
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "COORDINADOR")) return true;
        if (usuario != null && comentario.getAutor() != null
                && usuario.getId().equals(comentario.getAutor().getId())) return true;
        String audiencia = comentario.getAudiencia();
        if (hasRole(authentication, "ESTUDIANTE")) {
            return "ESTUDIANTE".equals(audiencia) || "TODOS".equals(audiencia);
        }
        if (hasRole(authentication, "TUTOR")) {
            return "TUTOR".equals(audiencia) || "TODOS".equals(audiencia);
        }
        return false;
    }

    private void validarAudienciaDelRol(String audiencia, Authentication authentication) {
        if (hasRole(authentication, "ESTUDIANTE") && !"TUTOR".equals(audiencia)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El estudiante solo puede enviar comentarios a su tutor.");
        }
        if (hasRole(authentication, "TUTOR")
                && !Set.of("ESTUDIANTE", "COORDINACION").contains(audiencia)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El tutor solo puede escribir al estudiante o a coordinación.");
        }
        if ((hasRole(authentication, "ADMIN") || hasRole(authentication, "COORDINADOR"))
                && !Set.of("ESTUDIANTE", "TUTOR", "TODOS").contains(audiencia)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Gestión académica debe dirigir el comentario al estudiante, tutor o a ambos.");
        }
    }

    private List<Usuario> destinatarios(String audiencia, Estudiante estudiante, Usuario tutor,
                                         String proceso, Usuario autor) {
        Map<Integer, Usuario> destinos = new LinkedHashMap<>();
        if (("ESTUDIANTE".equals(audiencia) || "TODOS".equals(audiencia))
                && estudiante != null && estudiante.getUsuario() != null) {
            destinos.put(estudiante.getUsuario().getId(), estudiante.getUsuario());
        }
        if (("TUTOR".equals(audiencia) || "TODOS".equals(audiencia)) && tutor != null) {
            destinos.put(tutor.getId(), tutor);
        }
        if ("COORDINACION".equals(audiencia) || "TODOS".equals(audiencia)) {
            String carrera = estudiante == null ? null : estudiante.getCarrera();
            if (carrera != null) {
                coordinadorRepository.findByCarreraIgnoreCase(carrera).stream()
                        .filter(fila -> procesoVisible(fila, proceso))
                        .map(CoordinadorCarrera::getUsuario)
                        .filter(destino -> destino != null && Boolean.TRUE.equals(destino.getActivo()))
                        .forEach(destino -> destinos.put(destino.getId(), destino));
            }
        }
        if (autor != null) destinos.remove(autor.getId());
        return List.copyOf(destinos.values());
    }

    private boolean procesoVisible(CoordinadorCarrera fila, String proceso) {
        String tipo = normalizar(fila.getCoordinacionTipo());
        return "AMBOS".equals(tipo) || proceso.equals(tipo);
    }

    private void verificarPuedeVer(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")
                && carreraVisible(practica.getEstudiante(), authentication)) return;
        if (hasRole(authentication, "TUTOR") && usuario != null && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE") && esEstudiantePropio(practica.getEstudiante(), usuario)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes acceso al seguimiento de esta práctica.");
    }

    private void verificarPuedeVer(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && alcanceCoordinador.procesoVisible(authentication, "VINCULACION")
                && carreraVisible(vinculacion.getEstudiante(), authentication)) return;
        if (hasRole(authentication, "TUTOR") && usuario != null && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE") && esEstudiantePropio(vinculacion.getEstudiante(), usuario)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No tienes acceso al seguimiento de esta vinculación.");
    }

    private boolean carreraVisible(Estudiante estudiante, Authentication authentication) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private boolean esEstudiantePropio(Estudiante estudiante, Usuario usuario) {
        return estudiante != null && estudiante.getUsuario() != null && usuario != null
                && usuario.getId().equals(estudiante.getUsuario().getId());
    }

    private void validarExpedienteActivo(String estado, Object cerradoEn) {
        if (cerradoEn != null || ESTADOS_TERMINALES.contains(normalizar(estado).toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("El expediente finalizado conserva su historial, pero ya no admite comentarios.");
        }
    }

    private ComentarioSeguimientoResponse respuesta(ComentarioSeguimiento comentario) {
        Usuario autor = comentario.getAutor();
        return new ComentarioSeguimientoResponse(
                comentario.getId(),
                comentario.getPractica() == null ? null : comentario.getPractica().getId(),
                comentario.getVinculacion() == null ? null : comentario.getVinculacion().getId(),
                autor == null ? null : autor.getId(),
                nombre(autor),
                rolPrincipal(autor),
                comentario.getAudiencia(),
                comentario.getMensaje(),
                comentario.getFechaCreacion());
    }

    private String rolPrincipal(Usuario usuario) {
        if (usuario == null || usuario.getRoles() == null) return "SISTEMA";
        return usuario.getRoles().stream().map(rol -> rol.getCodigo().toUpperCase(Locale.ROOT))
                .filter(Set.of("ADMIN", "COORDINADOR", "TUTOR", "ESTUDIANTE")::contains)
                .findFirst().orElse("SISTEMA");
    }

    private String nombre(Usuario usuario) {
        if (usuario == null) return "Sistema";
        String valor = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
        return valor.isBlank() ? usuario.getEmail() : valor;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
