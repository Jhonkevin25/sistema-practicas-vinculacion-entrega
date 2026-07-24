package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoria;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.proyecto.CuposProyectoComponent;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vinculacion/postulaciones")
@RequiredArgsConstructor
public class PostulacionVinculacionController {

    private static final String PROCESO = "VINCULACION";
    private static final Set<String> DOCUMENTOS_OBLIGATORIOS = Set.of("cv", "carta", "cedula");

    private final PostulacionVinculacionRepository postulacionRepository;
    private final VinculacionRepository vinculacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final ProyectoRepository proyectoRepository;
    private final PracticaRepository practicaRepository;
    private final DocEstudianteRepository documentoRepository;
    private final FechasConvocatoriaRepository fechasRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final PeriodoAcademicoComponent periodoComponent;
    private final CuposProyectoComponent cuposProyectoComponent;
    private final TutorAsignacionComponent tutorAsignacionComponent;

    @GetMapping
    public List<PostulacionVinculacion> getAll(Authentication authentication,
                                               @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> postulacionRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar postulaciones de vinculación.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<PostulacionVinculacion>of()
                        : postulacionRepository.findByEstudianteCarreraIn(carreras))
                .orElseGet(postulacionRepository::findAll);
    }

    @GetMapping("/me")
    public List<PostulacionVinculacion> getMisPostulaciones(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(estudiante -> postulacionRepository.findByEstudianteId(estudiante.getId()))
                .orElse(List.of());
    }

    @PostMapping
    @Transactional
    public PostulacionVinculacion postular(@RequestBody PostulacionVinculacion request,
                                           Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario) {
        if (!hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el estudiante puede postular a vinculación.");
        }
        Estudiante estudiante = estudianteActual(usuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        if (request.getProyecto() == null || request.getProyecto().getId() == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto de vinculación.");
        }
        Proyecto proyecto = proyectoRepository.findById(request.getProyecto().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));

        PeriodoAcademico periodo = periodoComponent.activo();
        validarConvocatoriaVigente(periodo.getCodigo());
        String periodoAcademico = periodo.getCodigo();
        validarDocumentosObligatorios(estudiante.getId());
        validarSinProcesoActivo(estudiante.getId());
        validarPeriodoReintento(estudiante.getId(), periodoAcademico);
        if (postulacionRepository.existsByEstudianteIdAndEstado(estudiante.getId(), "Pendiente")) {
            throw new IllegalArgumentException("Ya tienes una postulación de vinculación pendiente.");
        }
        if (!periodoAcademico.equals(proyecto.getPeriodo())) {
            throw new IllegalArgumentException(
                    "El proyecto seleccionado no pertenece al periodo académico activo.");
        }
        cuposProyectoComponent.exigirDisponible(proyecto, estudiante.getCarrera());
        convenioVigenteComponent.exigirParaFundacionEnPeriodo(
                proyecto.getFundacion(), estudiante.getCarrera(), periodo);

        PostulacionVinculacion postulacion = new PostulacionVinculacion();
        postulacion.setEstudiante(estudiante);
        postulacion.setProyecto(proyecto);
        postulacion.setEstado("Pendiente");
        postulacion.setPeriodoAcademico(periodoAcademico);
        postulacion.setCreatedAt(LocalDateTime.now());
        PostulacionVinculacion creada = postulacionRepository.save(postulacion);
        notificacionEmitter.emitir(estudiante.getUsuario(), "postulacion_enviada",
                "Postulación enviada",
                "Tu postulación al proyecto '" + proyecto.getNombre() + "' fue registrada y está pendiente de revisión.",
                "postulacion_vinculacion", creada.getId().longValue(), "/dashboard/vinculacion");
        return creada;
    }

    @PostMapping("/{id}/aprobar")
    @Transactional
    public PostulacionVinculacion aprobar(@PathVariable Integer id,
                                          @RequestBody Vinculacion request,
                                          Authentication authentication,
                                          @AuthenticationPrincipal Usuario usuario) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo gestión académica puede aprobar vinculación.");
        }
        PostulacionVinculacion postulacion = postulacionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        if (!"Pendiente".equals(postulacion.getEstado())) {
            throw new IllegalArgumentException("La postulación ya fue procesada.");
        }
        if (postulacion.getVinculacion() != null) {
            throw new IllegalArgumentException("La postulación ya tiene una vinculación oficial asociada.");
        }
        verificarPuedeGestionarEstudiante(postulacion.getEstudiante(), authentication);
        Estudiante estudiante = estudianteRepository.findByIdForUpdate(postulacion.getEstudiante().getId())
                .orElseThrow(() -> new IllegalArgumentException("El estudiante no existe."));
        validarSinProcesoActivo(estudiante.getId());
        Usuario tutor = tutorAsignacionComponent.exigirValido(request.getTutor(), PROCESO);
        Proyecto proyecto = proyectoRepository.findByIdForUpdate(postulacion.getProyecto().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
        PeriodoAcademico periodo = periodoComponent.exigirActivo(postulacion.getPeriodoAcademico());
        if (!periodo.getCodigo().equals(proyecto.getPeriodo())) {
            throw new IllegalArgumentException(
                    "La postulación y el proyecto no pertenecen al mismo periodo académico.");
        }
        convenioVigenteComponent.exigirParaFundacionEnPeriodo(
                proyecto.getFundacion(), estudiante.getCarrera(), periodo);
        cuposProyectoComponent.descontar(proyecto, estudiante.getCarrera());

        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setEstudiante(estudiante);
        vinculacion.setProyecto(proyecto);
        vinculacion.setFundacion(proyecto.getFundacion());
        vinculacion.setTutor(tutor);
        vinculacion.setEstado("en_curso");
        vinculacion.setHorasRequeridas(proyecto.getHorasRequeridas());
        vinculacion.setHorasCompletadas(0);
        vinculacion.setFechaInicio(LocalDate.now());
        vinculacion.setPeriodoAcademico(postulacion.getPeriodoAcademico());
        Vinculacion creada = vinculacionRepository.save(vinculacion);

        postulacion.setEstado("Aprobado");
        postulacion.setVinculacion(creada);
        postulacion.setAprobadoPor(usuario);
        postulacion.setAprobadoEn(LocalDateTime.now());
        PostulacionVinculacion guardada = postulacionRepository.save(postulacion);
        notificacionEmitter.emitir(guardada.getEstudiante().getUsuario(), "asignacion_vinculacion",
                "Vinculación asignada",
                "Tu postulación fue aprobada: se registró tu vinculación en el proyecto '" + proyecto.getNombre() + "'.",
                "vinculacion", creada.getId().longValue(), "/dashboard/vinculacion");
        notificacionEmitter.emitir(tutor, "asignacion_vinculacion",
                "Nueva tutoría de vinculación",
                "Se te asignó un nuevo expediente en el proyecto '" + proyecto.getNombre() + "'.",
                "vinculacion", creada.getId().longValue(), "/dashboard/vinculacion");
        return guardada;
    }

    @PostMapping("/{id}/rechazar")
    @Transactional
    public PostulacionVinculacion rechazar(@PathVariable Integer id,
                                           @RequestBody PostulacionVinculacion request,
                                           Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo gestión académica puede rechazar vinculación.");
        }
        PostulacionVinculacion postulacion = postulacionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postulación no encontrada."));
        verificarPuedeGestionarEstudiante(postulacion.getEstudiante(), authentication);
        if (!"Pendiente".equals(postulacion.getEstado())) {
            throw new IllegalArgumentException("La postulación ya fue procesada.");
        }
        postulacion.setEstado("Rechazado");
        postulacion.setObservacion(request.getObservacion());
        postulacion.setAprobadoPor(usuario);
        postulacion.setAprobadoEn(LocalDateTime.now());
        PostulacionVinculacion guardada = postulacionRepository.save(postulacion);
        notificacionEmitter.emitir(guardada.getEstudiante().getUsuario(), "postulacion_resuelta",
                "Postulación rechazada",
                "Tu postulación a vinculación fue rechazada. Revisa la observación de gestión académica.",
                "postulacion_vinculacion", guardada.getId().longValue(), "/dashboard/vinculacion");
        return guardada;
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private void validarDocumentosObligatorios(Integer estudianteId) {
        Set<String> docs = documentoRepository.findByEstudianteIdAndProceso(estudianteId, "VINCULACION").stream()
                .filter(doc -> doc.getUrlArchivo() != null && !doc.getUrlArchivo().isBlank())
                .filter(doc -> "aprobado".equals(doc.getEstado()))
                .map(DocEstudiante::getTipoDocumento)
                .collect(Collectors.toSet());
        if (!docs.containsAll(DOCUMENTOS_OBLIGATORIOS)) {
            throw new IllegalArgumentException(
                    "Tus documentos obligatorios de vinculacion deben estar aprobados antes de postular.");
        }
    }

    private void validarSinProcesoActivo(Integer estudianteId) {
        boolean vinculacionActiva = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "pendiente".equals(v.getEstado()) || "en_curso".equals(v.getEstado()));
        boolean practicaActiva = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "pendiente".equals(p.getEstado()) || "en_curso".equals(p.getEstado()));
        if (vinculacionActiva || practicaActiva) {
            throw new IllegalArgumentException("Ya tienes un proceso académico activo.");
        }
        boolean vinculacionCompletada = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "completado".equalsIgnoreCase(v.getEstado()));
        if (vinculacionCompletada) {
            throw new IllegalArgumentException(
                    "El estudiante ya completó una vinculación. Solo puede reintentarse después de una finalización excepcional.");
        }
    }

    private void validarPeriodoReintento(Integer estudianteId, String periodoAcademico) {
        boolean intentoExcepcionalEnMismoPeriodo = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> ("reprobado".equalsIgnoreCase(v.getEstado())
                        || "retirado".equalsIgnoreCase(v.getEstado()))
                        && periodoAcademico.equals(v.getPeriodoAcademico()));
        if (intentoExcepcionalEnMismoPeriodo) {
            throw new IllegalArgumentException(
                    "El reintento de vinculacion debe realizarse en otro periodo academico.");
        }
    }

    private void validarConvocatoriaVigente(String periodoAcademico) {
        FechasConvocatoria vigente = fechasRepository
                .findByPeriodoAcademicoAndTipo(periodoAcademico, "VINCULACION")
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe convocatoria de vinculación para el periodo académico activo."));
        LocalDate hoy = LocalDate.now();
        if (hoy.isBefore(vigente.getFechaInicioPostulacion()) || hoy.isAfter(vigente.getConvocatoriaFin())) {
            throw new IllegalArgumentException("La postulación a vinculación no está habilitada en la fecha actual.");
        }
    }

    private void verificarPuedeGestionarEstudiante(Estudiante estudiante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")) {
            boolean visible = alcanceCoordinador.procesoVisible(authentication, PROCESO)
                    && alcanceCoordinador.carrerasVisibles(authentication)
                    .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                    .orElse(false);
            if (visible) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar esta postulación.");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }
}
