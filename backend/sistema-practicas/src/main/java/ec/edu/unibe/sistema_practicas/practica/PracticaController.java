package ec.edu.unibe.sistema_practicas.practica;

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
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaComponent;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.seguimiento.FinalizacionExcepcionalRequest;
import ec.edu.unibe.sistema_practicas.seguimiento.SeguimientoTimelineComponent;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.LiberacionCupoRequest;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
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
@RequestMapping("/api/practicas")
@RequiredArgsConstructor
public class PracticaController {

    private static final String PROCESO = "PRACTICAS";

    // Regla: el expediente solo avanza hacia adelante (mismo estado siempre valido)
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "pendiente", Set.of("en_curso"),
            "en_curso", Set.of("completado"),
            "completado", Set.of()
    );

    private final PracticaRepository practicaRepository;
    private final EstudianteRepository estudianteRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final VacantePracticaRepository vacantePracticaRepository;
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
    private final TutorAsignacionComponent tutorAsignacionComponent;

    @GetMapping
    public List<Practica> getAll(Authentication authentication,
                                 @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> practicaRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (hasRole(authentication, "TUTOR")) {
            return usuario == null ? List.of() : practicaRepository.findByTutorId(usuario.getId());
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return practicasVisiblesGestion(authentication);
    }

    // Fase 43: paginación y filtros (texto del estudiante, estado, periodo)
    // sobre la consulta con alcance existente por rol.
    // Fase 43: paginación y filtros en base de datos
    @GetMapping("/paginado")
    public PaginaResponse<Practica> getPaginado(
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

        org.springframework.data.jpa.domain.Specification<Practica> spec = PracticaSpecification.build(
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
        
        org.springframework.data.domain.Page<Practica> pageResult = practicaRepository.findAll(spec, pageable);
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
    public List<Practica> getMisPracticas(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> practicaRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @GetMapping("/me/linea-tiempo")
    public List<ec.edu.unibe.sistema_practicas.seguimiento.LineaTiempoExpedienteResponse> getMiLineaTiempo(
            @AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(estudiante -> practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                        .map(seguimientoTimelineComponent::paraPractica)
                        .toList())
                .orElse(List.of());
    }

    @GetMapping("/tutor/me")
    public List<Practica> getMisTutorias(Authentication authentication,
                                         @AuthenticationPrincipal Usuario usuario) {
        if (!hasRole(authentication, "TUTOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el tutor puede consultar sus tutorías.");
        }
        return usuario == null ? List.of() : practicaRepository.findByTutorId(usuario.getId());
    }

    @GetMapping("/seguimiento")
    public List<SeguimientoPracticaResponse> getSeguimiento(Authentication authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo gestión académica puede consultar seguimiento.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return practicasVisiblesGestion(authentication).stream()
                .map(this::resumenSeguimiento)
                .toList();
    }

    // Fase 43: paginación de seguimiento en base de datos
    @GetMapping("/seguimiento/paginado")
    public PaginaResponse<SeguimientoPracticaResponse> getSeguimientoPaginado(
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

        org.springframework.data.jpa.domain.Specification<Practica> spec = PracticaSpecification.build(
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
        
        org.springframework.data.domain.Page<Practica> pageResult = practicaRepository.findAll(spec, pageable);
        
        List<SeguimientoPracticaResponse> contenido = pageResult.getContent().stream()
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
        Practica practica = practicaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVer(practica, authentication, usuario);
        return cierreExpedienteComponent.actaPractica(practica);
    }

    @GetMapping("/{id}/linea-tiempo")
    public ec.edu.unibe.sistema_practicas.seguimiento.LineaTiempoExpedienteResponse getLineaTiempo(
            @PathVariable Integer id,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVer(practica, authentication, usuario);
        return seguimientoTimelineComponent.paraPractica(practica);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Practica> getById(@PathVariable Integer id,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario) {
        return practicaRepository.findById(id)
                .map(practica -> {
                    verificarPuedeVer(practica, authentication, usuario);
                    return ResponseEntity.ok(practica);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<Practica> getByEstudianteId(@PathVariable Integer estudianteId,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario) {
        verificarPuedeVerEstudiante(estudianteId, authentication, usuario);
        return practicaRepository.findByEstudianteId(estudianteId);
    }

    @GetMapping("/tutor/{tutorId}")
    public List<Practica> getByTutorId(@PathVariable Integer tutorId,
                                       Authentication authentication,
                                       @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "TUTOR") && (usuario == null || !tutorId.equals(usuario.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar prácticas de otro tutor.");
        }
        if (hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar prácticas por tutor.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        List<Practica> practicas = practicaRepository.findByTutorId(tutorId);
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> practicas.stream()
                        .filter(p -> p.getEstudiante() != null && carreras.contains(p.getEstudiante().getCarrera()))
                        .toList())
                .orElse(practicas);
    }

    @GetMapping("/encargado/{encargadoId}")
    public List<Practica> getByEncargadoId(@PathVariable Integer encargadoId,
                                           Authentication authentication) {
        if (hasRole(authentication, "ESTUDIANTE") || hasRole(authentication, "TUTOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar prácticas por encargado.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        List<Practica> practicas = practicaRepository.findByEncargadoId(encargadoId);
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> practicas.stream()
                        .filter(p -> p.getEstudiante() != null && carreras.contains(p.getEstudiante().getCarrera()))
                        .toList())
                .orElse(practicas);
    }

    @PostMapping
    @Transactional
    public Practica create(@Valid @RequestBody Practica practica,
                           Authentication authentication) {
        if (practica.getEstudiante() == null || practica.getEstudiante().getId() == null) {
            throw new IllegalArgumentException("La práctica debe estar asociada a un estudiante.");
        }
        if (practica.getVacante() == null || practica.getVacante().getId() == null) {
            throw new IllegalArgumentException("La asignación directa debe indicar una vacante para descontar cupos.");
        }
        Integer estudianteId = practica.getEstudiante().getId();
        verificarPuedeGestionarEstudiante(estudianteId, authentication);
        Estudiante estudiante = estudianteRepository.findByIdForUpdate(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        // Regla: sin procesos activos duplicados para el mismo estudiante
        boolean tieneActiva = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "pendiente".equals(p.getEstado()) || "en_curso".equals(p.getEstado()));
        boolean vinculacionActiva = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "pendiente".equalsIgnoreCase(v.getEstado()) || "en_curso".equalsIgnoreCase(v.getEstado()));
        if (tieneActiva || vinculacionActiva) {
            throw new IllegalArgumentException(
                "El estudiante ya tiene un proceso académico activo. No puede registrar otra asignación hasta completarlo.");
        }
        if (practica.getHorasCompletadas() != null && practica.getHorasCompletadas() != 0) {
            throw new IllegalArgumentException(
                    "Las horas completadas se calculan desde bitácoras aprobadas y no pueden enviarse manualmente.");
        }
        VacantePractica vacante = vacantePracticaRepository.findByIdForUpdate(practica.getVacante().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacante no encontrada."));
        verificarPuedeGestionarVacante(vacante, authentication);
        if (!OfertaCuposEmpresaComponent.mismaCarrera(vacante.getCarrera(), estudiante.getCarrera())) {
            throw new IllegalArgumentException("La vacante no corresponde a la carrera del estudiante.");
        }
        validarEtapaPractica(estudiante, vacante);
        convenioVigenteComponent.exigirParaEmpresa(vacante.getEmpresa(), estudiante.getCarrera());
        descontarCupoVacante(vacante);
        if (vacante.getHoras() == null || vacante.getHoras() <= 0) {
            throw new IllegalArgumentException("La vacante debe definir horas requeridas mayores a cero.");
        }
        Usuario tutor = tutorAsignacionComponent.exigirValido(
                practica.getTutor(), PROCESO, vacante.getEmpresa(), estudiante.getCarrera());
        String tipoAsignacion = practica.getTipoAsignacion() == null || practica.getTipoAsignacion().isBlank()
                || "LEGACY".equalsIgnoreCase(practica.getTipoAsignacion())
                ? "DIRECTA"
                : practica.getTipoAsignacion().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DIRECTA", "CONTINUIDAD").contains(tipoAsignacion)) {
            throw new IllegalArgumentException("El tipo de asignación directa no es válido.");
        }
        validarContinuidad(practica, estudiante, vacante, tipoAsignacion);
        practica.setEstudiante(estudiante);
        practica.setEmpresa(vacante.getEmpresa());
        practica.setVacante(vacante);
        practica.setTutor(tutor);
        practica.setTipoAsignacion(tipoAsignacion);
        practica.setAsignacionSnapshot(objectMapper.writeValueAsString(snapshotAsignacion(vacante, tipoAsignacion,
                practica.getPracticaOrigenId())));
        practica.setPeriodoAcademico(vacante.getPeriodoAcademico());
        practica.setHorasRequeridas(vacante.getHoras());
        horasExpedienteComponent.aplicarHorasAprobadas(practica);
        Practica creada = practicaRepository.save(practica);
        Usuario destinatario = estudianteRepository.findById(creada.getEstudiante().getId())
                .map(Estudiante::getUsuario)
                .orElse(null);
        String empresa = vacante.getEmpresa() == null ? "la empresa asignada" : vacante.getEmpresa().getNombre();
        notificacionEmitter.emitir(destinatario, "asignacion_practica",
                "Práctica asignada",
                "Se registró tu práctica en " + empresa + ".",
                "practica", creada.getId().longValue(), "/dashboard/practicas");
        notificacionEmitter.emitir(tutor, "asignacion_practica",
                "Nueva tutoría de práctica",
                "Se te asignó la tutoría de " + nombreEstudiante(estudiante) + " en " + empresa + ".",
                "practica", creada.getId().longValue(), "/dashboard/practicas");
        return creada;
    }

    @PostMapping("/{id}/cerrar")
    @Transactional
    public Practica cerrar(@PathVariable Integer id,
                           Authentication authentication,
                           @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeGestionarEstudiante(practica.getEstudiante().getId(), authentication);
        if ("completado".equalsIgnoreCase(practica.getEstado())) {
            return practica;
        }
        if (esFinalizacionExcepcional(practica.getEstado())) {
            throw new IllegalArgumentException("Una práctica reprobada o retirada no puede cerrarse formalmente.");
        }
        horasExpedienteComponent.aplicarHorasAprobadas(practica);
        validarCierreFormal(practica);
        cierreExpedienteComponent.validarDocumentosCierre(practica.getEstudiante(), "PRACTICAS", null);
        practica.setEstado("completado");
        practica.setFechaFin(LocalDate.now());
        cierreExpedienteComponent.registrarCierrePractica(practica, usuario, snapshotCierre(practica));
        Practica cerrada = practicaRepository.save(practica);
        notificacionEmitter.emitir(
                cerrada.getEstudiante() == null ? null : cerrada.getEstudiante().getUsuario(),
                "expediente_cerrado",
                "Práctica cerrada",
                "Tu práctica fue cerrada oficialmente con todas las horas y evaluaciones completas.",
                "practica", cerrada.getId().longValue(), "/dashboard/practicas");
        notificacionEmitter.emitir(
                cerrada.getTutor(),
                "expediente_cerrado",
                "Tutoría de práctica cerrada",
                "El expediente de " + nombreEstudiante(cerrada.getEstudiante())
                        + " fue cerrado oficialmente y pasó a tu historial.",
                "practica", cerrada.getId().longValue(), "/dashboard/practicas");
        return cerrada;
    }

    @PostMapping("/{id}/finalizar-excepcional")
    @Transactional
    public Practica finalizarExcepcional(@PathVariable Integer id,
                                         @Valid @RequestBody FinalizacionExcepcionalRequest request,
                                         Authentication authentication,
                                         @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeGestionarEstudiante(practica.getEstudiante().getId(), authentication);

        String nuevoEstado = request.estado().toLowerCase(Locale.ROOT);
        String motivo = request.motivo().trim();
        if (motivo.length() < 10) {
            throw new IllegalArgumentException("El motivo de finalización debe tener al menos 10 caracteres.");
        }
        if (nuevoEstado.equals(practica.getEstado() == null ? "" : practica.getEstado().toLowerCase(Locale.ROOT))) {
            return practica;
        }
        if ("completado".equalsIgnoreCase(practica.getEstado())
                || esFinalizacionExcepcional(practica.getEstado())) {
            throw new IllegalArgumentException("La práctica ya está en un estado terminal y no puede cambiarse.");
        }
        if ("reprobado".equals(nuevoEstado) && !"en_curso".equalsIgnoreCase(practica.getEstado())) {
            throw new IllegalArgumentException("Una práctica solo puede marcarse como REPROBADO desde en_curso.");
        }
        if ("retirado".equals(nuevoEstado)
                && !Set.of("pendiente", "en_curso").contains(practica.getEstado() == null
                ? "" : practica.getEstado().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Una práctica solo puede marcarse como RETIRADO desde pendiente o en_curso.");
        }

        Map<String, Object> antes = snapshotCierre(practica);
        practica.setEstado(nuevoEstado);
        practica.setMotivoFinalizacion(motivo);
        practica.setFechaFin(LocalDate.now());
        practica.setCerradoPor(usuario);
        practica.setCerradoEn(java.time.LocalDateTime.now());
        Map<String, Object> despues = snapshotCierre(practica);
        practica.setCierreSnapshot(objectMapper.writeValueAsString(despues));
        Practica finalizada = practicaRepository.save(practica);
        auditoriaEmitter.registrar("PRACTICAS", "FINALIZACION_EXCEPCIONAL", usuario, antes, despues);
        notificacionEmitter.emitir(
                finalizada.getEstudiante() == null ? null : finalizada.getEstudiante().getUsuario(),
                "expediente_cerrado",
                "Práctica finalizada excepcionalmente",
                "Tu práctica fue finalizada como " + nuevoEstado.toUpperCase(Locale.ROOT)
                        + ". Motivo: " + motivo,
                "practica", finalizada.getId().longValue(), "/dashboard/practicas");
        notificacionEmitter.emitir(
                finalizada.getTutor(),
                "expediente_cerrado",
                "Tutoría de práctica finalizada",
                "El expediente de " + nombreEstudiante(finalizada.getEstudiante()) + " fue finalizado como "
                        + nuevoEstado.toUpperCase(Locale.ROOT) + ".",
                "practica", finalizada.getId().longValue(), "/dashboard/practicas");
        return finalizada;
    }

    private String nombreEstudiante(Estudiante estudiante) {
        if (estudiante == null || estudiante.getUsuario() == null) return "el estudiante";
        Usuario usuario = estudiante.getUsuario();
        String nombre = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
        return nombre.isBlank() ? "el estudiante" : nombre;
    }

    // Fase 47 (espejo de fase 42): un retiro conserva el cupo de la empresa por
    // defecto; gestión académica puede liberarlo UNA sola vez, con justificación,
    // para asignar un reemplazo dentro del mismo periodo. Los cupos ocupados se
    // derivan, así que basta marcar la práctica para excluirla del conteo.
    @PostMapping("/{id}/liberar-cupo")
    @Transactional
    public Practica liberarCupo(@PathVariable Integer id,
                                @Valid @RequestBody LiberacionCupoRequest request,
                                Authentication authentication,
                                @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeGestionarEstudiante(practica.getEstudiante().getId(), authentication);
        String motivo = request.motivo().trim();
        if (motivo.length() < 10) {
            throw new IllegalArgumentException("El motivo de liberación debe tener al menos 10 caracteres.");
        }
        if (!"retirado".equalsIgnoreCase(practica.getEstado())) {
            throw new IllegalArgumentException("Solo se puede liberar el cupo de una práctica retirada.");
        }
        if (Boolean.TRUE.equals(practica.getCupoLiberado())) {
            throw new IllegalArgumentException("El cupo de esta práctica ya fue liberado y no puede repetirse.");
        }
        // El reemplazo solo tiene sentido dentro de un periodo aún activo
        periodoComponent.exigirActivo(practica.getPeriodoAcademico());

        Map<String, Object> antes = snapshotCierre(practica);
        practica.setCupoLiberado(true);
        practica.setCupoLiberadoPor(usuario);
        practica.setCupoLiberadoEn(java.time.LocalDateTime.now());
        practica.setMotivoLiberacionCupo(motivo);
        Practica guardada = practicaRepository.save(practica);

        Map<String, Object> despues = new LinkedHashMap<>(snapshotCierre(guardada));
        despues.put("cupoLiberado", true);
        despues.put("motivoLiberacionCupo", motivo);
        auditoriaEmitter.registrar("PRACTICAS", "LIBERACION_CUPO", usuario, antes, despues);
        return guardada;
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Practica> update(@PathVariable Integer id, @Valid @RequestBody Practica details,
                                           Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario,
                                           @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        return practicaRepository.findById(id)
                .map(practica -> {
                    verificarPuedeActualizar(practica, authentication);
                    Integer tutorAnteriorId = practica.getTutor() == null ? null : practica.getTutor().getId();
                    cierreExpedienteComponent.validarModificacionPermitida(
                            practica, authentication, usuario, justificacionAdmin, "PRACTICAS", practica.getId());
                    if (!cierreExpedienteComponent.estaCerrada(practica)
                            && "completado".equalsIgnoreCase(details.getEstado())) {
                        throw new IllegalArgumentException("Usa el cierre formal para completar una práctica.");
                    }
                    if (details.getEstado() != null && !details.getEstado().equals(practica.getEstado())) {
                        Set<String> permitidos = TRANSICIONES.get(practica.getEstado());
                        if (permitidos == null || !permitidos.contains(details.getEstado())) {
                            throw new IllegalArgumentException(
                                "Transición de estado no permitida: la práctica está en '" + practica.getEstado()
                                + "' y no puede pasar a '" + details.getEstado() + "'.");
                        }
                        practica.setEstado(details.getEstado());
                    }
                    if (details.getHorasCompletadas() != null
                            && !Objects.equals(details.getHorasCompletadas(), practica.getHorasCompletadas())) {
                        throw new IllegalArgumentException(
                                "Las horas completadas se calculan desde bitácoras aprobadas y no pueden editarse.");
                    }
                    if (details.getHorasRequeridas() != null
                            && !Objects.equals(details.getHorasRequeridas(), practica.getHorasRequeridas())) {
                        throw new IllegalArgumentException(
                                "Las horas requeridas de la práctica provienen de la vacante y no pueden editarse.");
                    }
                    horasExpedienteComponent.aplicarHorasAprobadas(practica);
                    practica.setFechaInicio(details.getFechaInicio());
                    practica.setFechaFin(details.getFechaFin());
                    practica.setPeriodoAcademico(details.getPeriodoAcademico());

                    if (details.getEstudiante() != null) {
                        if (practica.getEstudiante() == null
                                || !Objects.equals(practica.getEstudiante().getId(), details.getEstudiante().getId())) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar el estudiante de una práctica existente.");
                        }
                        practica.setEstudiante(details.getEstudiante());
                    }
                    if (details.getEmpresa() != null) {
                        if (practica.getEmpresa() == null
                                || !Objects.equals(practica.getEmpresa().getId(), details.getEmpresa().getId())) {
                            throw new IllegalArgumentException(
                                    "No se puede cambiar la empresa sin una nueva asignación oficial.");
                        }
                        practica.setEmpresa(details.getEmpresa());
                    }
                    if (details.getTutor() != null) {
                        practica.setTutor(tutorAsignacionComponent.exigirValido(
                                details.getTutor(), PROCESO, practica.getEmpresa(),
                                practica.getEstudiante() == null ? null : practica.getEstudiante().getCarrera()));
                    }
                    if (details.getEncargado() != null) {
                        practica.setEncargado(details.getEncargado());
                    }

                    Practica guardada = practicaRepository.save(practica);
                    Integer tutorNuevoId = guardada.getTutor() == null ? null : guardada.getTutor().getId();
                    if (!Objects.equals(tutorAnteriorId, tutorNuevoId)) {
                        auditoriaEmitter.registrar("PRACTICAS", "CAMBIO_TUTOR", usuario,
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

    private List<Practica> practicasVisiblesGestion(Authentication authentication) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<Practica>of()
                        : practicaRepository.findByEstudianteCarreraIn(carreras))
                .orElseGet(practicaRepository::findAll);
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.procesoVisible(authentication, PROCESO)
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private void verificarPuedeVer(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, practica.getEstudiante())) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = estudianteActual(usuario);
            if (estudiante.isPresent()
                    && practica.getEstudiante() != null
                    && estudiante.get().getId().equals(practica.getEstudiante().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta práctica.");
    }

    private void verificarPuedeActualizar(Practica practica, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, practica.getEstudiante())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar esta práctica.");
    }

    private void verificarPuedeVerEstudiante(Integer estudianteId, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId).orElse(null);
        if (estudiante == null) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && practicaRepository.existsByEstudianteIdAndTutorId(estudianteId, usuario.getId())) return;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> actual = estudianteActual(usuario);
            if (actual.isPresent() && estudianteId.equals(actual.get().getId())) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las prácticas de este estudiante.");
    }

    private void verificarPuedeGestionarEstudiante(Integer estudianteId, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar prácticas de este estudiante.");
    }

    private void descontarCupoVacante(VacantePractica vacante) {
        if (Boolean.FALSE.equals(vacante.getActiva())) {
            throw new IllegalArgumentException("La vacante está pausada y no admite nuevas asignaciones.");
        }
        // Fase 42: un periodo cerrado bloquea el consumo de cupos
        periodoComponent.exigirActivo(vacante.getPeriodoAcademico());
        int cupos = vacante.getCupos() == null ? 0 : vacante.getCupos();
        if (cupos <= 0) {
            throw new IllegalArgumentException("La vacante seleccionada no tiene cupos disponibles.");
        }
        vacante.setCupos(cupos - 1);
        vacantePracticaRepository.save(vacante);
    }

    private void verificarPuedeGestionarVacante(VacantePractica vacante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")) {
            boolean visible = alcanceCoordinador.procesoVisible(authentication, PROCESO)
                    && alcanceCoordinador.carrerasVisibles(authentication)
                    .map(carreras -> vacante != null && carreras.contains(vacante.getCarrera()))
                    .orElse(false);
            if (visible) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes asignar cupos de esta vacante.");
    }

    private SeguimientoPracticaResponse resumenSeguimiento(Practica practica) {
        List<Bitacora> bitacoras = practica.getId() == null
                ? List.of()
                : bitacoraRepository.findByPracticaId(practica.getId());
        List<Asistencia> asistencias = practica.getId() == null
                ? List.of()
                : asistenciaRepository.findByPracticaId(practica.getId());
        List<EvaluacionPracticaDetalle> evaluaciones = practica.getId() == null
                ? List.of()
                : evaluacionRepository.findByPracticaId(practica.getId());
        List<EncuestaSatisfaccion> encuestas = practica.getId() == null
                ? List.of()
                : encuestaRepository.findByPracticaId(practica.getId());

        long bitacorasPendientes = bitacoras.stream()
                .filter(b -> "pendiente".equalsIgnoreCase(b.getEstado()))
                .count();
        long bitacorasRechazadas = bitacoras.stream()
                .filter(b -> "rechazada".equalsIgnoreCase(b.getEstado()))
                .count();
        long bitacorasCorreccion = bitacoras.stream()
                .filter(b -> "requiere_correccion".equalsIgnoreCase(b.getEstado()))
                .count();

        int horasCompletadas = practica.getHorasCompletadas() == null ? 0 : practica.getHorasCompletadas();
        int horasRequeridas = practica.getHorasRequeridas() == null || practica.getHorasRequeridas() <= 0
                ? 1
                : practica.getHorasRequeridas();
        int porcentajeAvance = Math.min(100, Math.round((horasCompletadas * 100f) / horasRequeridas));

        long asistenciasAtrasoFalta = asistencias.stream()
                .filter(a -> "Atraso".equalsIgnoreCase(a.getEstado()) || "Falta".equalsIgnoreCase(a.getEstado()))
                .count();
        Integer diasSinActividad = diasSinActividad(practica, bitacoras, asistencias);
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

        boolean terminal = esEstadoTerminalSeguimiento(practica.getEstado());
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
            if ("en_curso".equalsIgnoreCase(practica.getEstado())
                    && avanceBajoSegunRitmo(practica.getFechaInicio(), practica.getFechaFin(), porcentajeAvance)) {
                alertas.add("Avance bajo de horas");
            }
        }
        boolean listoParaCierre = !terminal && porcentajeAvance >= 100 && parcialesCerrados == 3;
        if (listoParaCierre) alertas.add("Listo para cierre");
        boolean riesgo = !terminal && alertas.stream().anyMatch(a -> !"Listo para cierre".equals(a));

        Estudiante estudiante = practica.getEstudiante();
        Usuario tutor = practica.getTutor();
        return new SeguimientoPracticaResponse(
                practica.getId(),
                estudiante == null ? null : estudiante.getId(),
                estudiante == null || estudiante.getUsuario() == null ? "Sin estudiante" :
                        estudiante.getUsuario().getNombre() + " " + estudiante.getUsuario().getApellido(),
                estudiante == null ? null : estudiante.getMatricula(),
                estudiante == null ? null : estudiante.getCarrera(),
                practica.getEmpresa() == null ? null : practica.getEmpresa().getNombre(),
                tutor == null ? "Sin asignar" : tutor.getNombre() + " " + tutor.getApellido(),
                practica.getPeriodoAcademico(),
                practica.getEstado(),
                practica.getHorasRequeridas(),
                practica.getHorasCompletadas(),
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

    private Integer diasSinActividad(Practica practica, List<Bitacora> bitacoras, List<Asistencia> asistencias) {
        Optional<LocalDate> ultimaBitacora = bitacoras.stream()
                .map(Bitacora::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        Optional<LocalDate> ultimaAsistencia = asistencias.stream()
                .map(Asistencia::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        Optional<LocalDate> fechaInicio = Optional.ofNullable(practica.getFechaInicio());
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

    private void validarCierreFormal(Practica practica) {
        int horasCompletadas = practica.getHorasCompletadas() == null ? 0 : practica.getHorasCompletadas();
        int horasRequeridas = practica.getHorasRequeridas() == null ? 0 : practica.getHorasRequeridas();
        if (horasRequeridas <= 0 || horasCompletadas < horasRequeridas) {
            throw new IllegalArgumentException("No se puede cerrar: faltan horas aprobadas.");
        }

        List<Bitacora> bitacoras = bitacoraRepository.findByPracticaId(practica.getId());
        boolean tieneBitacorasAbiertas = FlujoBitacora.tieneAbiertas(bitacoras);
        if (tieneBitacorasAbiertas) {
            throw new IllegalArgumentException("No se puede cerrar: existen bitácoras pendientes o por corregir.");
        }

        List<EvaluacionPracticaDetalle> evaluaciones = evaluacionRepository.findByPracticaId(practica.getId());
        List<EncuestaSatisfaccion> encuestas = encuestaRepository.findByPracticaId(practica.getId());
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

    private void validarEtapaPractica(Estudiante estudiante, VacantePractica vacante) {
        if (vacante.getPeriodoAcademico() == null || vacante.getPeriodoAcademico().isBlank()) {
            throw new IllegalArgumentException("La vacante debe indicar el periodo academico.");
        }
        validarPeriodoReintento(estudiante.getId(), vacante.getPeriodoAcademico());
        boolean vinculacionCompletada = vinculacionRepository.findByEstudianteId(estudiante.getId()).stream()
                .anyMatch(v -> "completado".equalsIgnoreCase(v.getEstado()));
        if (!vinculacionCompletada) {
            throw new IllegalArgumentException(
                    "La vinculación debe estar completada antes de asignar una práctica.");
        }
        long practicasCompletadas = practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                .filter(p -> "completado".equalsIgnoreCase(p.getEstado()))
                .count();
        if (practicasCompletadas >= 2) {
            throw new IllegalArgumentException(
                    "El estudiante ya completó Práctica I y Práctica II; no puede recibir otra práctica.");
        }
        String etapaEsperada = practicasCompletadas == 0 ? "Práctica I" : "Práctica II";
        if (!etapaEsperada.equals(vacante.getModalidadAcademica())) {
            throw new IllegalArgumentException(
                    "La vacante debe corresponder a la etapa académica " + etapaEsperada + ".");
        }
    }

    private void validarContinuidad(Practica solicitud, Estudiante estudiante, VacantePractica vacante,
                                    String tipoAsignacion) {
        if (!"CONTINUIDAD".equals(tipoAsignacion)) {
            solicitud.setPracticaOrigenId(null);
            return;
        }
        if (solicitud.getPracticaOrigenId() == null) {
            throw new IllegalArgumentException("La continuidad debe indicar la práctica anterior.");
        }
        Practica origen = practicaRepository.findById(solicitud.getPracticaOrigenId())
                .orElseThrow(() -> new IllegalArgumentException("La práctica anterior no existe."));
        if (!"completado".equalsIgnoreCase(origen.getEstado())) {
            throw new IllegalArgumentException("Solo una práctica completada puede originar una continuidad.");
        }
        if (origen.getEstudiante() == null || !estudiante.getId().equals(origen.getEstudiante().getId())) {
            throw new IllegalArgumentException("La práctica anterior no pertenece al estudiante indicado.");
        }
        if (origen.getEmpresa() == null || vacante.getEmpresa() == null
                || !origen.getEmpresa().getId().equals(vacante.getEmpresa().getId())) {
            throw new IllegalArgumentException("La continuidad debe conservar la misma empresa.");
        }
    }

    private Map<String, Object> snapshotAsignacion(VacantePractica vacante, String tipoAsignacion,
                                                    Integer practicaOrigenId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tipoAsignacion", tipoAsignacion);
        snapshot.put("practicaOrigenId", practicaOrigenId);
        snapshot.put("vacanteId", vacante.getId());
        snapshot.put("vacante", vacante.getNombre());
        snapshot.put("empresaId", vacante.getEmpresa() == null ? null : vacante.getEmpresa().getId());
        snapshot.put("empresa", vacante.getEmpresa() == null ? null : vacante.getEmpresa().getNombre());
        snapshot.put("area", vacante.getArea());
        snapshot.put("descripcion", vacante.getDescripcion());
        snapshot.put("requisitos", vacante.getRequisitos());
        snapshot.put("perfilRequerido", vacante.getPerfilRequerido());
        snapshot.put("modalidadAcademica", vacante.getModalidadAcademica());
        snapshot.put("modalidadTrabajo", vacante.getModalidadTrabajo());
        snapshot.put("ciudad", vacante.getCiudad());
        snapshot.put("horas", vacante.getHoras());
        snapshot.put("periodoAcademico", vacante.getPeriodoAcademico());
        return snapshot;
    }

    private void validarPeriodoReintento(Integer estudianteId, String periodoAcademico) {
        boolean intentoExcepcionalEnMismoPeriodo = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> ("reprobado".equalsIgnoreCase(p.getEstado())
                        || "retirado".equalsIgnoreCase(p.getEstado()))
                        && periodoAcademico.equals(p.getPeriodoAcademico()));
        if (intentoExcepcionalEnMismoPeriodo) {
            throw new IllegalArgumentException(
                    "El reintento de practicas debe realizarse en otro periodo academico.");
        }
    }

    private Map<String, Object> snapshotCierre(Practica practica) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Estudiante estudiante = practica.getEstudiante();
        snapshot.put("proceso", "PRACTICAS");
        snapshot.put("expedienteId", practica.getId());
        snapshot.put("fechaCierre", practica.getFechaFin());
        snapshot.put("periodoAcademico", practica.getPeriodoAcademico());
        snapshot.put("estado", practica.getEstado());
        snapshot.put("motivoFinalizacion", practica.getMotivoFinalizacion());
        snapshot.put("horasRequeridas", practica.getHorasRequeridas());
        snapshot.put("horasCompletadas", practica.getHorasCompletadas());
        snapshot.put("estudianteId", estudiante == null ? null : estudiante.getId());
        snapshot.put("matricula", estudiante == null ? null : estudiante.getMatricula());
        snapshot.put("carrera", estudiante == null ? null : estudiante.getCarrera());
        snapshot.put("entidad", practica.getEmpresa() == null ? null : practica.getEmpresa().getNombre());
        snapshot.put("tutorId", practica.getTutor() == null ? null : practica.getTutor().getId());
        snapshot.put("notas", evaluacionRepository.findByPracticaId(practica.getId()).stream()
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

    public record SeguimientoPracticaResponse(
            Integer practicaId,
            Integer estudianteId,
            String estudiante,
            String matricula,
            String carrera,
            String empresa,
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
