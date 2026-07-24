package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.bitacora.FlujoBitacora;
import ec.edu.unibe.sistema_practicas.bitacora.HorasExpedienteComponent;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccion;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccionRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalle;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalleRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.proyecto.CuposProyectoComponent;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.seguimiento.FinalizacionExcepcionalRequest;
import ec.edu.unibe.sistema_practicas.seguimiento.SeguimientoTimelineComponent;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

@RestController
@RequestMapping("/api/vinculacion")
@RequiredArgsConstructor
public class VinculacionController {

    private static final String PROCESO = "VINCULACION";

    // Regla: el expediente solo avanza hacia adelante (mismo estado siempre valido)
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "pendiente", Set.of("en_curso"),
            "en_curso", Set.of("completado"),
            "completado", Set.of()
    );

    private final VinculacionRepository vinculacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final PracticaRepository practicaRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ProyectoRepository proyectoRepository;
    private final BitacoraRepository bitacoraRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EvaluacionPracticaDetalleRepository evaluacionRepository;
    private final EncuestaSatisfaccionRepository encuestaRepository;
    private final CierreExpedienteComponent cierreExpedienteComponent;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final NotificacionEmitter notificacionEmitter;
    private final AuditoriaEmitter auditoriaEmitter;
    private final ObjectMapper objectMapper;
    private final SeguimientoTimelineComponent seguimientoTimelineComponent;
    private final HorasExpedienteComponent horasExpedienteComponent;
    private final PeriodoAcademicoComponent periodoComponent;
    private final CuposProyectoComponent cuposProyectoComponent;
    private final TutorAsignacionComponent tutorAsignacionComponent;

    @GetMapping
    public List<Vinculacion> getAll(Authentication authentication,
                                    @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> vinculacionRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (hasRole(authentication, "TUTOR")) {
            return usuario == null ? List.of() : vinculacionRepository.findByTutorId(usuario.getId());
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return vinculacionesVisiblesGestion(authentication);
    }

    // Fase 43: paginación y filtros (texto del estudiante, estado, periodo)
    // sobre la consulta con alcance existente por rol.
    // Fase 43: paginación y filtros en base de datos
    @GetMapping("/paginado")
    public PaginaResponse<Vinculacion> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String periodoAcademico,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        
        Integer estudianteId = null;
        Integer tutorId = null;
        java.util.Collection<String> carrerasGestion = null;
        boolean restringirProcesoGestion = false;

        if (hasRole(authentication, "ESTUDIANTE")) {
            estudianteId = estudianteActual(usuario).map(Estudiante::getId).orElse(-1);
        } else if (hasRole(authentication, "TUTOR")) {
            tutorId = usuario == null ? -1 : usuario.getId();
        } else {
            restringirProcesoGestion = !alcanceCoordinador.procesoVisible(authentication, PROCESO);
            carrerasGestion = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);
        }

        org.springframework.data.jpa.domain.Specification<Vinculacion> spec = VinculacionSpecification.build(
                texto, estado, periodoAcademico, estudianteId, tutorId, carrerasGestion, restringirProcesoGestion);
        
        org.springframework.data.domain.Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Sort sort;
        if (sortBy.equalsIgnoreCase("nombre") || sortBy.equalsIgnoreCase("apellido")) {
            sort = org.springframework.data.domain.Sort.by(direction, "estudiante.usuario.apellido").and(org.springframework.data.domain.Sort.by(direction, "estudiante.usuario.nombre"));
        } else {
            // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
            String campoOrden = List.of("id", "estado", "periodoAcademico", "fechaInicio", "fechaFin")
                    .contains(sortBy) ? sortBy : "id";
            sort = org.springframework.data.domain.Sort.by(direction, campoOrden);
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pagina, tamano, sort);
        
        org.springframework.data.domain.Page<Vinculacion> pageResult = vinculacionRepository.findAll(spec, pageable);
        return PaginaResponse.desde(pageResult);
    }

    private boolean coincideEstudiante(Estudiante estudiante, String buscado) {
        if (estudiante == null) return false;
        String matricula = estudiante.getMatricula() == null ? "" : estudiante.getMatricula();
        String nombre = estudiante.getUsuario() == null ? "" :
                estudiante.getUsuario().getNombre() + " " + estudiante.getUsuario().getApellido();
        return nombre.toLowerCase(Locale.ROOT).contains(buscado)
                || matricula.toLowerCase(Locale.ROOT).contains(buscado);
    }

    @GetMapping("/me")
    public List<Vinculacion> getMisVinculaciones(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> vinculacionRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @GetMapping("/me/linea-tiempo")
    public List<ec.edu.unibe.sistema_practicas.seguimiento.LineaTiempoExpedienteResponse> getMiLineaTiempo(
            @AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(estudiante -> vinculacionRepository.findByEstudianteId(estudiante.getId()).stream()
                        .map(seguimientoTimelineComponent::paraVinculacion)
                        .toList())
                .orElse(List.of());
    }

    @GetMapping("/tutor/me")
    public List<Vinculacion> getMisTutorias(Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario) {
        if (!hasRole(authentication, "TUTOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el tutor puede consultar sus tutorías.");
        }
        return usuario == null ? List.of() : vinculacionRepository.findByTutorId(usuario.getId());
    }

    @GetMapping("/seguimiento")
    public List<SeguimientoVinculacionResponse> getSeguimiento(Authentication authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo gestión académica puede consultar seguimiento.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return vinculacionesVisiblesGestion(authentication).stream()
                .map(this::resumenSeguimiento)
                .toList();
    }

    // Fase 43: paginación de seguimiento en base de datos
    @GetMapping("/seguimiento/paginado")
    public PaginaResponse<SeguimientoVinculacionResponse> getSeguimientoPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String periodoAcademico,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) Integer tutorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo gestión académica puede consultar seguimiento.");
        }

        boolean restringirProcesoGestion = !alcanceCoordinador.procesoVisible(authentication, PROCESO);
        java.util.Collection<String> carrerasGestion = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);

        // Filtro de carrera pedido por la UI: se interseca con el alcance para
        // que un coordinador no pueda consultar carreras fuera de su alcance.
        if (carrera != null && !carrera.isBlank()) {
            String carreraBuscada = carrera.trim();
            if (carrerasGestion == null) {
                carrerasGestion = List.of(carreraBuscada);
            } else {
                carrerasGestion = carrerasGestion.stream()
                        .filter(c -> c.equalsIgnoreCase(carreraBuscada))
                        .toList();
            }
        }

        org.springframework.data.jpa.domain.Specification<Vinculacion> spec = VinculacionSpecification.build(
                texto, estado, periodoAcademico, null, tutorId, carrerasGestion, restringirProcesoGestion);
        
        org.springframework.data.domain.Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Sort sort;
        if (sortBy.equalsIgnoreCase("nombre") || sortBy.equalsIgnoreCase("apellido")) {
            sort = org.springframework.data.domain.Sort.by(direction, "estudiante.usuario.apellido").and(org.springframework.data.domain.Sort.by(direction, "estudiante.usuario.nombre"));
        } else {
            // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
            String campoOrden = List.of("id", "estado", "periodoAcademico", "fechaInicio", "fechaFin")
                    .contains(sortBy) ? sortBy : "id";
            sort = org.springframework.data.domain.Sort.by(direction, campoOrden);
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pagina, tamano, sort);
        
        org.springframework.data.domain.Page<Vinculacion> pageResult = vinculacionRepository.findAll(spec, pageable);
        
        List<SeguimientoVinculacionResponse> contenido = pageResult.getContent().stream()
                .map(this::resumenSeguimiento)
                .toList();

        return new PaginaResponse<>(
            contenido,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages()
        );
    }

    @GetMapping("/{id}/acta")
    public Map<String, Object> getActa(@PathVariable Integer id,
                                       Authentication authentication,
                                       @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVer(vinculacion, authentication, usuario);
        return cierreExpedienteComponent.actaVinculacion(vinculacion);
    }

    @GetMapping("/{id}/linea-tiempo")
    public ec.edu.unibe.sistema_practicas.seguimiento.LineaTiempoExpedienteResponse getLineaTiempo(
            @PathVariable Integer id,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVer(vinculacion, authentication, usuario);
        return seguimientoTimelineComponent.paraVinculacion(vinculacion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vinculacion> getById(@PathVariable Integer id,
                                               Authentication authentication,
                                               @AuthenticationPrincipal Usuario usuario) {
        return vinculacionRepository.findById(id)
                .map(vinculacion -> {
                    verificarPuedeVer(vinculacion, authentication, usuario);
                    return ResponseEntity.ok(vinculacion);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<Vinculacion> getByEstudianteId(@PathVariable Integer estudianteId,
                                               Authentication authentication,
                                               @AuthenticationPrincipal Usuario usuario) {
        verificarPuedeVerEstudiante(estudianteId, authentication, usuario);
        return vinculacionRepository.findByEstudianteId(estudianteId);
    }

    @GetMapping("/tutor/{tutorId}")
    public List<Vinculacion> getByTutorId(@PathVariable Integer tutorId,
                                          Authentication authentication,
                                          @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "TUTOR") && (usuario == null || !tutorId.equals(usuario.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar vinculaciones de otro tutor.");
        }
        if (hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar vinculaciones por tutor.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        List<Vinculacion> vinculaciones = vinculacionRepository.findByTutorId(tutorId);
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> vinculaciones.stream()
                        .filter(v -> v.getEstudiante() != null && carreras.contains(v.getEstudiante().getCarrera()))
                        .toList())
                .orElse(vinculaciones);
    }

    @GetMapping("/encargado/{encargadoId}")
    public List<Vinculacion> getByEncargadoId(@PathVariable Integer encargadoId,
                                              Authentication authentication) {
        if (hasRole(authentication, "ESTUDIANTE") || hasRole(authentication, "TUTOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar vinculaciones por encargado.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        List<Vinculacion> vinculaciones = vinculacionRepository.findByEncargadoId(encargadoId);
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> vinculaciones.stream()
                        .filter(v -> v.getEstudiante() != null && carreras.contains(v.getEstudiante().getCarrera()))
                        .toList())
                .orElse(vinculaciones);
    }

    @PostMapping
    @Transactional
    public Vinculacion create(@Valid @RequestBody Vinculacion vinculacion,
                              Authentication authentication) {
        if (vinculacion.getEstudiante() == null || vinculacion.getEstudiante().getId() == null) {
            throw new IllegalArgumentException("La vinculación debe estar asociada a un estudiante.");
        }
        if (vinculacion.getProyecto() == null || vinculacion.getProyecto().getId() == null) {
            throw new IllegalArgumentException("La vinculación debe estar asociada a un proyecto.");
        }
        Integer estudianteId = vinculacion.getEstudiante().getId();
        verificarPuedeGestionarEstudiante(estudianteId, authentication);
        Estudiante estudiante = estudianteRepository.findByIdForUpdate(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        // Regla: sin procesos activos duplicados para el mismo estudiante
        boolean tieneActiva = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "pendiente".equals(v.getEstado()) || "en_curso".equals(v.getEstado()));
        boolean practicaActiva = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "pendiente".equalsIgnoreCase(p.getEstado()) || "en_curso".equalsIgnoreCase(p.getEstado()));
        boolean vinculacionCompletada = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "completado".equalsIgnoreCase(v.getEstado()));
        if (tieneActiva || practicaActiva) {
            throw new IllegalArgumentException(
                "El estudiante ya tiene un proceso académico activo. No puede registrar otra asignación hasta completarlo.");
        }
        if (vinculacionCompletada) {
            throw new IllegalArgumentException(
                    "El estudiante ya completó una vinculación. Solo puede reintentarse después de una finalización excepcional.");
        }
        if (vinculacion.getHorasCompletadas() != null && vinculacion.getHorasCompletadas() != 0) {
            throw new IllegalArgumentException(
                    "Las horas completadas se calculan desde bitácoras aprobadas y no pueden enviarse manualmente.");
        }
        Proyecto proyecto = proyectoRepository.findByIdForUpdate(vinculacion.getProyecto().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
        PeriodoAcademico periodo = periodoComponent.exigirActivo(proyecto.getPeriodo());
        validarPeriodoReintento(estudiante.getId(), periodo.getCodigo());
        convenioVigenteComponent.exigirParaFundacionEnPeriodo(
                proyecto.getFundacion(), estudiante.getCarrera(), periodo);
        cuposProyectoComponent.descontar(proyecto, estudiante.getCarrera());
        vinculacion.setEstudiante(estudiante);
        vinculacion.setTutor(tutorAsignacionComponent.exigirValido(vinculacion.getTutor(), PROCESO));
        vinculacion.setProyecto(proyecto);
        vinculacion.setFundacion(proyecto.getFundacion());
        vinculacion.setPeriodoAcademico(periodo.getCodigo());
        vinculacion.setHorasRequeridas(proyecto.getHorasRequeridas());
        vinculacion.setHorasCompletadas(0);
        vinculacion.setEstado("en_curso");
        vinculacion.setFechaInicio(LocalDate.now());
        horasExpedienteComponent.aplicarHorasAprobadas(vinculacion);
        Vinculacion creada = vinculacionRepository.save(vinculacion);
        Usuario destinatario = estudianteRepository.findById(creada.getEstudiante().getId())
                .map(Estudiante::getUsuario)
                .orElse(null);
        notificacionEmitter.emitir(destinatario, "asignacion_vinculacion",
                "Vinculación asignada",
                "Se registró tu vinculación en el proyecto '" + proyecto.getNombre() + "'.",
                "vinculacion", creada.getId().longValue(), "/dashboard/vinculacion");
        notificacionEmitter.emitir(creada.getTutor(), "asignacion_vinculacion",
                "Nueva tutoría de vinculación",
                "Se te asignó la tutoría de " + nombreEstudiante(estudiante)
                        + " en el proyecto '" + proyecto.getNombre() + "'.",
                "vinculacion", creada.getId().longValue(), "/dashboard/vinculacion");
        return creada;
    }

    @PostMapping("/{id}/cerrar")
    @Transactional
    public Vinculacion cerrar(@PathVariable Integer id,
                              Authentication authentication,
                              @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeGestionarEstudiante(vinculacion.getEstudiante().getId(), authentication);
        if ("completado".equalsIgnoreCase(vinculacion.getEstado())) {
            return vinculacion;
        }
        if (esFinalizacionExcepcional(vinculacion.getEstado())) {
            throw new IllegalArgumentException("Una vinculación reprobada o retirada no puede cerrarse formalmente.");
        }
        horasExpedienteComponent.aplicarHorasAprobadas(vinculacion);
        validarCierreFormal(vinculacion);
        cierreExpedienteComponent.validarDocumentosCierre(vinculacion.getEstudiante(), "VINCULACION", null);
        vinculacion.setEstado("completado");
        vinculacion.setFechaFin(LocalDate.now());
        cierreExpedienteComponent.registrarCierreVinculacion(vinculacion, usuario, snapshotCierre(vinculacion));
        Vinculacion cerrada = vinculacionRepository.save(vinculacion);
        notificacionEmitter.emitir(
                cerrada.getEstudiante() == null ? null : cerrada.getEstudiante().getUsuario(),
                "expediente_cerrado",
                "Vinculación cerrada",
                "Tu vinculación fue cerrada oficialmente con todas las horas, notas y encuestas completas.",
                "vinculacion", cerrada.getId().longValue(), "/dashboard/vinculacion");
        notificacionEmitter.emitir(
                cerrada.getTutor(),
                "expediente_cerrado",
                "Tutoría de vinculación cerrada",
                "El expediente de " + nombreEstudiante(cerrada.getEstudiante())
                        + " fue cerrado oficialmente y pasó a tu historial.",
                "vinculacion", cerrada.getId().longValue(), "/dashboard/vinculacion");
        return cerrada;
    }

    @PostMapping("/{id}/finalizar-excepcional")
    @Transactional
    public Vinculacion finalizarExcepcional(@PathVariable Integer id,
                                            @Valid @RequestBody FinalizacionExcepcionalRequest request,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeGestionarEstudiante(vinculacion.getEstudiante().getId(), authentication);

        String nuevoEstado = request.estado().toLowerCase(Locale.ROOT);
        String motivo = request.motivo().trim();
        if (motivo.length() < 10) {
            throw new IllegalArgumentException("El motivo de finalización debe tener al menos 10 caracteres.");
        }
        if (nuevoEstado.equals(vinculacion.getEstado() == null ? "" : vinculacion.getEstado().toLowerCase(Locale.ROOT))) {
            return vinculacion;
        }
        if ("completado".equalsIgnoreCase(vinculacion.getEstado())
                || esFinalizacionExcepcional(vinculacion.getEstado())) {
            throw new IllegalArgumentException("La vinculación ya está en un estado terminal y no puede cambiarse.");
        }
        if ("reprobado".equals(nuevoEstado) && !"en_curso".equalsIgnoreCase(vinculacion.getEstado())) {
            throw new IllegalArgumentException("Una vinculación solo puede marcarse como REPROBADO desde en_curso.");
        }
        if ("retirado".equals(nuevoEstado)
                && !Set.of("pendiente", "en_curso").contains(vinculacion.getEstado() == null
                ? "" : vinculacion.getEstado().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Una vinculación solo puede marcarse como RETIRADO desde pendiente o en_curso.");
        }

        Map<String, Object> antes = snapshotCierre(vinculacion);
        vinculacion.setEstado(nuevoEstado);
        vinculacion.setMotivoFinalizacion(motivo);
        vinculacion.setFechaFin(LocalDate.now());
        vinculacion.setCerradoPor(usuario);
        vinculacion.setCerradoEn(java.time.LocalDateTime.now());
        Map<String, Object> despues = snapshotCierre(vinculacion);
        vinculacion.setCierreSnapshot(objectMapper.writeValueAsString(despues));
        Vinculacion finalizada = vinculacionRepository.save(vinculacion);
        auditoriaEmitter.registrar("VINCULACION", "FINALIZACION_EXCEPCIONAL", usuario, antes, despues);
        notificacionEmitter.emitir(
                finalizada.getEstudiante() == null ? null : finalizada.getEstudiante().getUsuario(),
                "expediente_cerrado",
                "Vinculación finalizada excepcionalmente",
                "Tu vinculación fue finalizada como " + nuevoEstado.toUpperCase(Locale.ROOT)
                        + ". Motivo: " + motivo,
                "vinculacion", finalizada.getId().longValue(), "/dashboard/vinculacion");
        notificacionEmitter.emitir(
                finalizada.getTutor(),
                "expediente_cerrado",
                "Tutoría de vinculación finalizada",
                "El expediente de " + nombreEstudiante(finalizada.getEstudiante()) + " fue finalizado como "
                        + nuevoEstado.toUpperCase(Locale.ROOT) + ".",
                "vinculacion", finalizada.getId().longValue(), "/dashboard/vinculacion");
        return finalizada;
    }

    private String nombreEstudiante(Estudiante estudiante) {
        if (estudiante == null || estudiante.getUsuario() == null) return "el estudiante";
        Usuario usuario = estudiante.getUsuario();
        String nombre = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
        return nombre.isBlank() ? "el estudiante" : nombre;
    }

    // Fase 42: un retiro conserva el cupo por defecto; gestión académica puede
    // liberarlo UNA sola vez, con justificación, para asignar un reemplazo
    // dentro del mismo periodo. Transaccional y auditado.
    @PostMapping("/{id}/liberar-cupo")
    @Transactional
    public Vinculacion liberarCupo(@PathVariable Integer id,
                                   @Valid @RequestBody LiberacionCupoRequest request,
                                   Authentication authentication,
                                   @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeGestionarEstudiante(vinculacion.getEstudiante().getId(), authentication);
        String motivo = request.motivo().trim();
        if (motivo.length() < 10) {
            throw new IllegalArgumentException("El motivo de liberación debe tener al menos 10 caracteres.");
        }
        if (!"retirado".equalsIgnoreCase(vinculacion.getEstado())) {
            throw new IllegalArgumentException("Solo se puede liberar el cupo de una vinculación retirada.");
        }
        if (Boolean.TRUE.equals(vinculacion.getCupoLiberado())) {
            throw new IllegalArgumentException("El cupo de esta vinculación ya fue liberado y no puede repetirse.");
        }
        // El reemplazo solo tiene sentido dentro de un periodo aún activo
        periodoComponent.exigirActivo(vinculacion.getPeriodoAcademico());
        Proyecto proyecto = proyectoRepository.findByIdForUpdate(vinculacion.getProyecto().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));

        Map<String, Object> antes = snapshotCierre(vinculacion);
        cuposProyectoComponent.reintegrar(proyecto, vinculacion.getEstudiante().getCarrera());
        vinculacion.setCupoLiberado(true);
        vinculacion.setCupoLiberadoPor(usuario);
        vinculacion.setCupoLiberadoEn(java.time.LocalDateTime.now());
        vinculacion.setMotivoLiberacionCupo(motivo);
        Vinculacion guardada = vinculacionRepository.save(vinculacion);

        Map<String, Object> despues = new LinkedHashMap<>(snapshotCierre(guardada));
        despues.put("cupoLiberado", true);
        despues.put("motivoLiberacionCupo", motivo);
        auditoriaEmitter.registrar("VINCULACION", "LIBERACION_CUPO", usuario, antes, despues);
        return guardada;
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Vinculacion> update(@PathVariable Integer id, @Valid @RequestBody Vinculacion details,
                                              Authentication authentication,
                                              @AuthenticationPrincipal Usuario usuario,
                                              @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        return vinculacionRepository.findById(id)
                .map(vinculacion -> {
                    verificarPuedeActualizar(vinculacion, authentication);
                    Integer tutorAnteriorId = vinculacion.getTutor() == null ? null : vinculacion.getTutor().getId();
                    cierreExpedienteComponent.validarModificacionPermitida(
                            vinculacion, authentication, usuario, justificacionAdmin, "VINCULACION", vinculacion.getId());
                    if (!cierreExpedienteComponent.estaCerrada(vinculacion)
                            && "completado".equalsIgnoreCase(details.getEstado())) {
                        throw new IllegalArgumentException("Usa el cierre formal para completar una vinculación.");
                    }
                    if (details.getEstado() != null && !details.getEstado().equals(vinculacion.getEstado())) {
                        Set<String> permitidos = TRANSICIONES.get(vinculacion.getEstado());
                        if (permitidos == null || !permitidos.contains(details.getEstado())) {
                            throw new IllegalArgumentException(
                                "Transición de estado no permitida: la vinculación está en '" + vinculacion.getEstado()
                                + "' y no puede pasar a '" + details.getEstado() + "'.");
                        }
                        vinculacion.setEstado(details.getEstado());
                    }
                    if (details.getHorasCompletadas() != null
                            && !Objects.equals(details.getHorasCompletadas(), vinculacion.getHorasCompletadas())) {
                        throw new IllegalArgumentException(
                                "Las horas completadas se calculan desde bitácoras aprobadas y no pueden editarse.");
                    }
                    if (details.getHorasRequeridas() != null && details.getHorasRequeridas() <= 0) {
                        throw new IllegalArgumentException("Las horas requeridas deben ser mayores a cero.");
                    }
                    if (details.getHorasRequeridas() != null) {
                        vinculacion.setHorasRequeridas(details.getHorasRequeridas());
                    }
                    horasExpedienteComponent.aplicarHorasAprobadas(vinculacion);
                    vinculacion.setFechaInicio(details.getFechaInicio());
                    vinculacion.setFechaFin(details.getFechaFin());
                    vinculacion.setPeriodoAcademico(details.getPeriodoAcademico());

                    if (details.getEstudiante() != null) {
                        if (vinculacion.getEstudiante() == null
                                || !Objects.equals(vinculacion.getEstudiante().getId(), details.getEstudiante().getId())) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar el estudiante de una vinculación existente.");
                        }
                        vinculacion.setEstudiante(details.getEstudiante());
                    }
                    if (details.getFundacion() != null) {
                        if (vinculacion.getFundacion() == null
                                || !Objects.equals(vinculacion.getFundacion().getId(), details.getFundacion().getId())) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar la fundación sin una nueva asignación oficial.");
                        }
                        vinculacion.setFundacion(details.getFundacion());
                    }
                    if (details.getProyecto() != null) {
                        if (vinculacion.getProyecto() == null
                                || !Objects.equals(vinculacion.getProyecto().getId(), details.getProyecto().getId())) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar el proyecto sin una nueva asignación oficial.");
                        }
                        vinculacion.setProyecto(details.getProyecto());
                    }
                    if (details.getTutor() != null) {
                        vinculacion.setTutor(tutorAsignacionComponent.exigirValido(details.getTutor(), PROCESO));
                    }
                    if (details.getEncargado() != null) {
                        vinculacion.setEncargado(details.getEncargado());
                    }

                    Vinculacion guardada = vinculacionRepository.save(vinculacion);
                    Integer tutorNuevoId = guardada.getTutor() == null ? null : guardada.getTutor().getId();
                    if (!Objects.equals(tutorAnteriorId, tutorNuevoId)) {
                        auditoriaEmitter.registrar("VINCULACION", "CAMBIO_TUTOR", usuario,
                                snapshotTutor(guardada.getId(), tutorAnteriorId),
                                snapshotTutor(guardada.getId(), tutorNuevoId));
                    }
                    return ResponseEntity.ok(guardada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> snapshotTutor(Integer expedienteId, Integer tutorId) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("expedienteId", expedienteId);
        datos.put("tutorId", tutorId);
        return datos;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id,
                                       Authentication authentication,
                                       @AuthenticationPrincipal Usuario usuario,
                                       @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        throw new IllegalArgumentException(
                "No se puede eliminar físicamente un expediente oficial. Usa la finalización excepcional como retiro para preservar el historial.");
    }

    private boolean esFinalizacionExcepcional(String estado) {
        return "reprobado".equalsIgnoreCase(estado) || "retirado".equalsIgnoreCase(estado);
    }

    private boolean esEstadoTerminalSeguimiento(String estado) {
        return "completado".equalsIgnoreCase(estado) || esFinalizacionExcepcional(estado);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private List<Vinculacion> vinculacionesVisiblesGestion(Authentication authentication) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<Vinculacion>of()
                        : vinculacionRepository.findByEstudianteCarreraIn(carreras))
                .orElseGet(vinculacionRepository::findAll);
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.procesoVisible(authentication, PROCESO)
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private void verificarPuedeVer(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, vinculacion.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = estudianteActual(usuario);
            if (estudiante.isPresent()
                    && vinculacion.getEstudiante() != null
                    && estudiante.get().getId().equals(vinculacion.getEstudiante().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta vinculación.");
    }

    private void verificarPuedeActualizar(Vinculacion vinculacion, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, vinculacion.getEstudiante())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar esta vinculación.");
    }

    private void verificarPuedeVerEstudiante(Integer estudianteId, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId).orElse(null);
        if (estudiante == null) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && vinculacionRepository.existsByEstudianteIdAndTutorId(estudianteId, usuario.getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> actual = estudianteActual(usuario);
            if (actual.isPresent() && estudianteId.equals(actual.get().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las vinculaciones de este estudiante.");
    }

    private void verificarPuedeGestionarEstudiante(Integer estudianteId, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar vinculaciones de este estudiante.");
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

    private SeguimientoVinculacionResponse resumenSeguimiento(Vinculacion vinculacion) {
        List<Bitacora> bitacoras = vinculacion.getId() == null
                ? List.of()
                : bitacoraRepository.findByVinculacionId(vinculacion.getId());
        List<Asistencia> asistencias = vinculacion.getId() == null
                ? List.of()
                : asistenciaRepository.findByVinculacionId(vinculacion.getId());
        List<EvaluacionPracticaDetalle> evaluaciones = vinculacion.getId() == null
                ? List.of()
                : evaluacionRepository.findByVinculacionId(vinculacion.getId());
        List<EncuestaSatisfaccion> encuestas = vinculacion.getId() == null
                ? List.of()
                : encuestaRepository.findByVinculacionId(vinculacion.getId());

        long bitacorasPendientes = bitacoras.stream()
                .filter(b -> "pendiente".equalsIgnoreCase(b.getEstado()))
                .count();
        long bitacorasRechazadas = bitacoras.stream()
                .filter(b -> "rechazada".equalsIgnoreCase(b.getEstado()))
                .count();
        long bitacorasCorreccion = bitacoras.stream()
                .filter(b -> "requiere_correccion".equalsIgnoreCase(b.getEstado()))
                .count();

        int horasCompletadas = vinculacion.getHorasCompletadas() == null ? 0 : vinculacion.getHorasCompletadas();
        int horasRequeridas = vinculacion.getHorasRequeridas() == null || vinculacion.getHorasRequeridas() <= 0
                ? 1
                : vinculacion.getHorasRequeridas();
        int porcentajeAvance = Math.min(100, Math.round((horasCompletadas * 100f) / horasRequeridas));

        long asistenciasAtrasoFalta = asistencias.stream()
                .filter(a -> "Atraso".equalsIgnoreCase(a.getEstado()) || "Falta".equalsIgnoreCase(a.getEstado()))
                .count();
        Integer diasSinActividad = diasSinActividad(vinculacion, bitacoras, asistencias);
        int notasTutorPendientes = 0;
        int notasCoordPendientes = 0;
        int encuestasPendientes = 0;
        int parcialesCerrados = 0;

        for (int parcial = 1; parcial <= 3; parcial++) {
            int p = parcial;
            Optional<EvaluacionPracticaDetalle> evaluacion = evaluaciones.stream()
                    .filter(e -> Integer.valueOf(p).equals(e.getParcial()))
                    .findFirst();
            boolean tieneBitacoras = bitacoras.stream().anyMatch(b -> Integer.valueOf(p).equals(b.getParcial()));
            boolean tieneActividad = tieneBitacoras || evaluacion.isPresent();
            boolean notaTutor = evaluacion.map(EvaluacionPracticaDetalle::getNotaTutor).filter(this::notaRegistrada).isPresent();
            boolean notaCoord = evaluacion.map(EvaluacionPracticaDetalle::getNotaCoord).filter(this::notaRegistrada).isPresent();
            boolean encuestaCompleta = evaluacion
                    .map(EvaluacionPracticaDetalle::getEncuestaCompletada)
                    .filter(Boolean::booleanValue)
                    .isPresent()
                    || encuestas.stream().anyMatch(e -> Integer.valueOf(p).equals(e.getParcial()));
            boolean bitacorasAbiertas = FlujoBitacora.tieneAbiertas(bitacoras, p);

            if (tieneActividad && !notaTutor) notasTutorPendientes++;
            if (tieneActividad && !notaCoord) notasCoordPendientes++;
            if (notaTutor && notaCoord && !encuestaCompleta) encuestasPendientes++;
            if (notaTutor && notaCoord && encuestaCompleta && !bitacorasAbiertas) parcialesCerrados++;
        }

        boolean terminal = esEstadoTerminalSeguimiento(vinculacion.getEstado());
        List<String> alertas = new ArrayList<>();
        if (!terminal) {
            if (bitacorasPendientes > 0) alertas.add("Bitácoras pendientes");
            // Una observada corregida (con aprobada posterior del mismo parcial) ya no alerta
            if (observadasSinResolver(bitacoras) > 0) alertas.add("Bitácoras con observaciones");
            if (notasTutorPendientes > 0) alertas.add("Notas de tutor pendientes");
            if (notasCoordPendientes > 0) alertas.add("Notas de coordinación pendientes");
            if (encuestasPendientes > 0) alertas.add("Encuestas pendientes");
            if (asistenciasAtrasoFalta > 0) alertas.add("Atrasos o faltas registradas");
            if (diasSinActividad != null && diasSinActividad >= 14) alertas.add("Sin actividad reciente");
            if ("en_curso".equalsIgnoreCase(vinculacion.getEstado())
                    && avanceBajoSegunRitmo(vinculacion.getFechaInicio(), vinculacion.getFechaFin(), porcentajeAvance)) {
                alertas.add("Avance bajo de horas");
            }
        }
        boolean listoParaCierre = !terminal && porcentajeAvance >= 100 && parcialesCerrados == 3;
        if (listoParaCierre) alertas.add("Listo para cierre");
        boolean riesgo = !terminal && alertas.stream().anyMatch(a -> !"Listo para cierre".equals(a));

        Estudiante estudiante = vinculacion.getEstudiante();
        Usuario tutor = vinculacion.getTutor();
        return new SeguimientoVinculacionResponse(
                vinculacion.getId(),
                estudiante == null ? null : estudiante.getId(),
                estudiante == null || estudiante.getUsuario() == null ? "Sin estudiante" :
                        estudiante.getUsuario().getNombre() + " " + estudiante.getUsuario().getApellido(),
                estudiante == null ? null : estudiante.getMatricula(),
                estudiante == null ? null : estudiante.getCarrera(),
                vinculacion.getProyecto() == null ? null : vinculacion.getProyecto().getNombre(),
                tutor == null ? "Sin asignar" : tutor.getNombre() + " " + tutor.getApellido(),
                vinculacion.getPeriodoAcademico(),
                vinculacion.getEstado(),
                vinculacion.getHorasRequeridas(),
                vinculacion.getHorasCompletadas(),
                porcentajeAvance,
                (int) bitacorasPendientes,
                (int) bitacorasRechazadas,
                (int) bitacorasCorreccion,
                asistencias.size(),
                diasSinActividad,
                parcialesCerrados,
                encuestas.size(),
                notasTutorPendientes,
                notasCoordPendientes,
                encuestasPendientes,
                listoParaCierre,
                riesgo,
                alertas
        );
    }

    private Integer diasSinActividad(Vinculacion vinculacion, List<Bitacora> bitacoras, List<Asistencia> asistencias) {
        Optional<LocalDate> ultimaBitacora = bitacoras.stream()
                .map(Bitacora::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        Optional<LocalDate> ultimaAsistencia = asistencias.stream()
                .map(Asistencia::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        Optional<LocalDate> fechaInicio = Optional.ofNullable(vinculacion.getFechaInicio());
        LocalDate ultima = List.of(ultimaBitacora, ultimaAsistencia, fechaInicio).stream()
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return ultima == null ? null : (int) ChronoUnit.DAYS.between(ultima, LocalDate.now());
    }

    private boolean notaRegistrada(Double nota) {
        return nota != null && nota > 0;
    }

    // Una bitácora observada (rechazada o por corregir) se considera resuelta
    // cuando su parcial tiene una bitácora aprobada posterior (la corrección)
    private long observadasSinResolver(List<Bitacora> bitacoras) {
        return bitacoras.stream()
                .filter(b -> "rechazada".equalsIgnoreCase(b.getEstado())
                        || "requiere_correccion".equalsIgnoreCase(b.getEstado()))
                .filter(b -> bitacoras.stream().noneMatch(otra ->
                        "aprobada".equalsIgnoreCase(otra.getEstado())
                                && Objects.equals(otra.getParcial(), b.getParcial())
                                && esPosterior(otra, b)))
                .count();
    }

    private boolean esPosterior(Bitacora otra, Bitacora base) {
        if (otra.getId() != null && base.getId() != null) return otra.getId() > base.getId();
        return true;
    }

    // El avance bajo solo alerta cuando el expediente ya lleva recorrido (14
    // días) y va por debajo de la mitad del ritmo esperado según sus fechas,
    // para no marcar en riesgo a los recién iniciados
    private boolean avanceBajoSegunRitmo(LocalDate inicio, LocalDate fin, int porcentajeAvance) {
        LocalDate hoy = LocalDate.now();
        if (inicio == null) return false;
        long transcurridos = ChronoUnit.DAYS.between(inicio, hoy);
        if (transcurridos < 14) return false;
        if (fin == null || !fin.isAfter(inicio)) return porcentajeAvance < 50;
        long totales = Math.max(1, ChronoUnit.DAYS.between(inicio, fin));
        int esperado = (int) Math.min(100, (transcurridos * 100) / totales);
        return porcentajeAvance < Math.max(1, esperado / 2);
    }

    private void validarCierreFormal(Vinculacion vinculacion) {
        int horasCompletadas = vinculacion.getHorasCompletadas() == null ? 0 : vinculacion.getHorasCompletadas();
        int horasRequeridas = vinculacion.getHorasRequeridas() == null ? 0 : vinculacion.getHorasRequeridas();
        if (horasRequeridas <= 0 || horasCompletadas < horasRequeridas) {
            throw new IllegalArgumentException("No se puede cerrar: faltan horas aprobadas.");
        }

        List<Bitacora> bitacoras = bitacoraRepository.findByVinculacionId(vinculacion.getId());
        if (bitacoras.isEmpty()) {
            throw new IllegalArgumentException("No se puede cerrar: no existen bitácoras de vinculación.");
        }
        boolean tieneBitacorasAbiertas = FlujoBitacora.tieneAbiertas(bitacoras);
        if (tieneBitacorasAbiertas) {
            throw new IllegalArgumentException("No se puede cerrar: existen bitácoras pendientes o por corregir.");
        }

        List<EvaluacionPracticaDetalle> evaluaciones = evaluacionRepository.findByVinculacionId(vinculacion.getId());
        List<EncuestaSatisfaccion> encuestas = encuestaRepository.findByVinculacionId(vinculacion.getId());
        for (int parcial = 1; parcial <= 3; parcial++) {
            int p = parcial;
            EvaluacionPracticaDetalle evaluacion = evaluaciones.stream()
                    .filter(e -> Integer.valueOf(p).equals(e.getParcial()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se puede cerrar: falta evaluación del parcial " + p + "."));
            boolean encuestaCompleta = Boolean.TRUE.equals(evaluacion.getEncuestaCompletada())
                    || encuestas.stream().anyMatch(e -> Integer.valueOf(p).equals(e.getParcial()));
            if (!notaRegistrada(evaluacion.getNotaTutor())
                    || !notaRegistrada(evaluacion.getNotaCoord())
                    || !encuestaCompleta) {
                throw new IllegalArgumentException(
                        "No se puede cerrar: el parcial " + p + " no está cerrado con notas y encuesta.");
            }
        }
    }

    private Map<String, Object> snapshotCierre(Vinculacion vinculacion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Estudiante estudiante = vinculacion.getEstudiante();
        snapshot.put("proceso", "VINCULACION");
        snapshot.put("expedienteId", vinculacion.getId());
        snapshot.put("fechaCierre", vinculacion.getFechaFin());
        snapshot.put("periodoAcademico", vinculacion.getPeriodoAcademico());
        snapshot.put("estado", vinculacion.getEstado());
        snapshot.put("motivoFinalizacion", vinculacion.getMotivoFinalizacion());
        snapshot.put("horasRequeridas", vinculacion.getHorasRequeridas());
        snapshot.put("horasCompletadas", vinculacion.getHorasCompletadas());
        snapshot.put("estudianteId", estudiante == null ? null : estudiante.getId());
        snapshot.put("matricula", estudiante == null ? null : estudiante.getMatricula());
        snapshot.put("carrera", estudiante == null ? null : estudiante.getCarrera());
        snapshot.put("fundacion", vinculacion.getFundacion() == null ? null : vinculacion.getFundacion().getNombre());
        snapshot.put("proyecto", vinculacion.getProyecto() == null ? null : vinculacion.getProyecto().getNombre());
        snapshot.put("tutorId", vinculacion.getTutor() == null ? null : vinculacion.getTutor().getId());
        snapshot.put("notas", evaluacionRepository.findByVinculacionId(vinculacion.getId()).stream()
                .map(e -> {
                    Map<String, Object> nota = new LinkedHashMap<>();
                    nota.put("parcial", e.getParcial());
                    nota.put("notaTutor", e.getNotaTutor());
                    nota.put("notaCoord", e.getNotaCoord());
                    nota.put("notaFinal", e.getNotaFinal());
                    nota.put("encuestaCompletada", e.getEncuestaCompletada());
                    return nota;
                })
                .toList());
        return snapshot;
    }

    public record SeguimientoVinculacionResponse(
            Integer vinculacionId,
            Integer estudianteId,
            String estudiante,
            String matricula,
            String carrera,
            String proyecto,
            String tutor,
            String periodoAcademico,
            String estado,
            Integer horasRequeridas,
            Integer horasCompletadas,
            Integer porcentajeAvance,
            Integer bitacorasPendientes,
            Integer bitacorasRechazadas,
            Integer bitacorasCorreccion,
            Integer asistenciasRegistradas,
            Integer diasSinActividad,
            Integer parcialesCerrados,
            Integer encuestasCompletadas,
            Integer notasTutorPendientes,
            Integer notasCoordPendientes,
            Integer encuestasPendientes,
            Boolean listoParaCierre,
            Boolean riesgo,
            List<String> alertas
    ) {}
}
