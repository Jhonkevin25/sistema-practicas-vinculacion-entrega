package ec.edu.unibe.sistema_practicas.postulacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoria;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademicaRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaComponent;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/postulaciones")
@RequiredArgsConstructor
public class PostulacionMeritocraticaController {

    private static final String PROCESO = "PRACTICAS";
    private static final int SEMESTRE_MINIMO_VINCULACION = 4;

    // Regla: transiciones de estado permitidas (mismo estado siempre valido)
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "Pendiente", Set.of("Procesado", "Rechazado"),
            "Procesado", Set.of("Aprobado", "Rechazado"),
            "Aprobado", Set.of(),
            "Rechazado", Set.of()
    );

    private final PostulacionMeritocraticaRepository postulacionMeritocraticaRepository;
    private final FechasConvocatoriaRepository fechasConvocatoriaRepository;
    private final PracticaRepository practicaRepository;
    private final DocEstudianteRepository documentoRepository;
    private final EstudianteRepository estudianteRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final VacantePracticaRepository vacantePracticaRepository;
    private final NotaAcademicaRepository notaAcademicaRepository;
    private final AuditoriaEmitter auditoriaEmitter;
    private final VinculacionRepository vinculacionRepository;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final NotificacionEmitter notificacionEmitter;
    private final PeriodoAcademicoComponent periodoComponent;
    private final TutorAsignacionComponent tutorAsignacionComponent;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<PostulacionMeritocratica> getAll(Authentication authentication,
                                                @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> postulacionMeritocraticaRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<PostulacionMeritocratica>of()
                        : postulacionMeritocraticaRepository.findByEstudianteCarreraIn(carreras))
                .orElseGet(postulacionMeritocraticaRepository::findAll);
    }

    @GetMapping("/me")
    public List<PostulacionMeritocratica> getMisPostulaciones(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> postulacionMeritocraticaRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @PostMapping
    public PostulacionMeritocratica create(@Valid @RequestBody PostulacionMeritocratica postulacion,
                                           Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            Estudiante estudiante = estudianteActual(usuario)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tu usuario no tiene un expediente de estudiante asociado."));
            postulacion.setEstudiante(estudiante);
        } else {
            if (postulacion.getEstudiante() == null || postulacion.getEstudiante().getId() == null) {
                throw new IllegalArgumentException("La postulación debe estar asociada a un estudiante.");
            }
            verificarPuedeGestionarEstudiante(postulacion.getEstudiante().getId(), authentication);
        }
        Integer estudianteId = postulacion.getEstudiante().getId();
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        postulacion.setEstudiante(estudiante);

        validarPreferencias(postulacion);
        validarVacantesParaEstudiante(postulacion, estudiante, authentication);

        // Regla: documentacion obligatoria completa antes de postular
        validarDocumentosObligatorios(estudianteId);

        // Regla: sin postulaciones duplicadas en proceso
        boolean enProceso = postulacionMeritocraticaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "Pendiente".equals(p.getEstado()) || "Procesado".equals(p.getEstado()));
        if (enProceso) {
            throw new IllegalArgumentException(
                "Ya tienes una postulación en proceso. Espera a que sea procesada antes de postular nuevamente.");
        }

        // Regla: sin practica activa simultanea
        boolean practicaActiva = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "pendiente".equals(p.getEstado()) || "en_curso".equals(p.getEstado()));
        boolean vinculacionActiva = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "pendiente".equalsIgnoreCase(v.getEstado()) || "en_curso".equalsIgnoreCase(v.getEstado()));
        if (practicaActiva || vinculacionActiva) {
            throw new IllegalArgumentException(
                "Ya tienes un proceso académico activo. No puedes postular a una nueva práctica hasta completarlo.");
        }

        // Regla: dentro del periodo de postulacion de la convocatoria vigente
        // (si no hay convocatoria configurada, no se restringe)
        String tipoConvocatoria = tipoConvocatoriaParaEtapa(etapaAcademica(estudiante));
        Optional<FechasConvocatoria> vigente = fechasConvocatoriaRepository.findAll().stream()
                .filter(f -> tipoConvocatoria.equals(f.getTipo()))
                .max(Comparator.comparing(FechasConvocatoria::getPeriodoAcademico));
        if (vigente.isPresent()) {
            LocalDate hoy = LocalDate.now();
            if (hoy.isBefore(vigente.get().getFechaInicioPostulacion())) {
                throw new IllegalArgumentException(
                    "La postulación aún no está habilitada. Inicia el " + vigente.get().getFechaInicioPostulacion() + ".");
            }
            if (hoy.isAfter(vigente.get().getConvocatoriaFin())) {
                throw new IllegalArgumentException(
                    "La convocatoria cerró el " + vigente.get().getConvocatoriaFin() + ". No es posible postular fuera de fecha.");
            }
        }

        double promedioOficial = calcularPromedioAcademicoAnterior(estudiante);
        postulacion.setPromedio(promedioOficial);
        postulacion.setScore(calcularScore(promedioOficial));
        postulacion.setEstado("Pendiente");
        postulacion.setAsignadoEmpresa(null);
        postulacion.setAsignadoVacante(null);
        postulacion.setAjustadoManualmente(false);
        postulacion.setJustificacionAjuste(null);

        PostulacionMeritocratica creada = postulacionMeritocraticaRepository.save(postulacion);
        notificacionEmitter.emitir(estudiante.getUsuario(), "postulacion_enviada",
                "Postulación enviada",
                "Tu postulación a prácticas fue registrada y está pendiente de procesamiento.",
                "postulacion_meritocratica", creada.getId().longValue(), "/dashboard/practicas");
        return creada;
    }

    @PostMapping("/procesar-meritocracia")
    @Transactional
    public List<PostulacionMeritocratica> procesarMeritocracia(Authentication authentication) {
        List<PostulacionMeritocratica> candidatas = postulacionMeritocraticaRepository.findByEstado("Pendiente").stream()
                .filter(post -> post.getEstudiante() != null)
                .filter(post -> puedeGestionarEstudiante(post.getEstudiante(), authentication))
                .sorted(Comparator.comparing(PostulacionMeritocratica::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<Integer, Integer> cuposSimulados = new HashMap<>();
        List<PostulacionMeritocratica> procesadas = new ArrayList<>();
        for (PostulacionMeritocratica post : candidatas) {
            VacantePractica asignada = primeraVacanteConCupo(post, cuposSimulados, authentication);
            post.setEstado("Procesado");
            post.setAjustadoManualmente(false);
            post.setJustificacionAjuste(null);
            if (asignada != null) {
                cuposSimulados.put(asignada.getId(), cuposDisponiblesSimulados(asignada, cuposSimulados) - 1);
                post.setAsignadoEmpresa(asignada.getEmpresa());
                post.setAsignadoVacante(asignada);
            } else {
                post.setAsignadoEmpresa(null);
                post.setAsignadoVacante(null);
            }
            procesadas.add(postulacionMeritocraticaRepository.save(post));
        }
        return procesadas;
    }

    // Procesamiento del coordinador: estado, asignacion y ajuste manual
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<PostulacionMeritocratica> update(@PathVariable Integer id,
                                                           @RequestBody PostulacionMeritocratica details,
                                                           Authentication authentication,
                                                           @AuthenticationPrincipal Usuario usuario) {
        return postulacionMeritocraticaRepository.findByIdForUpdate(id)
                .map(post -> {
                    verificarPuedeGestionarEstudiante(post.getEstudiante().getId(), authentication);
                    boolean aprobar = details.getEstado() != null
                            && !details.getEstado().equals(post.getEstado())
                            && "Aprobado".equals(details.getEstado());
                    boolean rechazar = details.getEstado() != null
                            && !details.getEstado().equals(post.getEstado())
                            && "Rechazado".equals(details.getEstado());
                    if (details.getEstado() != null && !details.getEstado().equals(post.getEstado())) {
                        Set<String> permitidos = TRANSICIONES.get(post.getEstado());
                        if (permitidos == null || !permitidos.contains(details.getEstado())) {
                            throw new IllegalArgumentException(
                                "Transición de estado no permitida: la postulación está en '" + post.getEstado()
                                + "' y no puede pasar a '" + details.getEstado() + "'.");
                        }
                        post.setEstado(details.getEstado());
                    }
                    VacantePractica asignadoVacante = null;
                    if (aprobar || (details.getAsignadoVacante() != null && details.getAsignadoVacante().getId() != null)) {
                        asignadoVacante = resolverVacanteAsignada(post, details);
                        verificarPuedeGestionarVacante(asignadoVacante, authentication);
                        validarVacanteCompatible(asignadoVacante, post.getEstudiante());
                        if (!aprobar) {
                            validarJustificacionAjuste(details);
                        }
                    }
                    if (aprobar) {
                        validarSinProcesoActivo(post.getEstudiante().getId());
                        convenioVigenteComponent.exigirParaEmpresa(
                                asignadoVacante.getEmpresa(), post.getEstudiante().getCarrera());
                        descontarCupoVacante(asignadoVacante);
                        post.setAsignadoEmpresa(asignadoVacante.getEmpresa());
                    } else if (asignadoVacante != null) {
                        post.setAsignadoEmpresa(asignadoVacante.getEmpresa());
                    } else if (details.getAsignadoEmpresa() != null) {
                        post.setAsignadoEmpresa(details.getAsignadoEmpresa());
                    }
                    if (asignadoVacante != null) {
                        post.setAsignadoVacante(asignadoVacante);
                    }
                    if (details.getAjustadoManualmente() != null) {
                        post.setAjustadoManualmente(details.getAjustadoManualmente());
                    }
                    if (details.getJustificacionAjuste() != null) {
                        post.setJustificacionAjuste(details.getJustificacionAjuste());
                    }
                    if (Boolean.TRUE.equals(post.getAjustadoManualmente())) {
                        validarJustificacionAjuste(post);
                        registrarAuditoriaAjuste(post, asignadoVacante, usuario);
                    }
                    PostulacionMeritocratica guardada = postulacionMeritocraticaRepository.save(post);
                    if (aprobar || rechazar) {
                        notificacionEmitter.emitir(
                                guardada.getEstudiante() == null ? null : guardada.getEstudiante().getUsuario(),
                                "postulacion_resuelta",
                                aprobar ? "Postulación aprobada" : "Postulación rechazada",
                                aprobar
                                        ? "Tu postulación a prácticas fue aprobada."
                                        : "Tu postulación a prácticas fue rechazada.",
                                "postulacion_meritocratica", guardada.getId().longValue(), "/dashboard/practicas");
                    }
                    return ResponseEntity.ok(guardada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/consolidar-practica")
    @Transactional
    public ResponseEntity<Practica> consolidarPractica(@PathVariable Integer id,
                                                       @RequestBody(required = false) Practica details,
                                                       Authentication authentication) {
        return postulacionMeritocraticaRepository.findByIdForUpdate(id)
                .map(post -> {
                    verificarPuedeGestionarEstudiante(post.getEstudiante().getId(), authentication);
                    if (!"Procesado".equals(post.getEstado()) && !"Aprobado".equals(post.getEstado())) {
                        throw new IllegalArgumentException(
                                "Solo se puede consolidar una postulación en estado Procesado o Aprobado.");
                    }

                    VacantePractica asignadoVacante = details != null && details.getVacante() != null
                            && details.getVacante().getId() != null
                                    ? vacantePracticaRepository.findByIdForUpdate(details.getVacante().getId())
                                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                    "Vacante asignada no encontrada."))
                                    : resolverVacanteAsignada(post, post);
                    verificarPuedeGestionarVacante(asignadoVacante, authentication);
                    convenioVigenteComponent.exigirParaEmpresa(
                            asignadoVacante.getEmpresa(), post.getEstudiante().getCarrera());
                    if (!"Aprobado".equals(post.getEstado())) {
                        descontarCupoVacante(asignadoVacante);
                        post.setEstado("Aprobado");
                        post.setAsignadoEmpresa(asignadoVacante.getEmpresa());
                        postulacionMeritocraticaRepository.save(post);
                    }

                    if ("Aprobado".equals(post.getEstado())) {
                        Optional<Practica> existente = practicaRepository
                                .findByEstudianteIdAndEmpresaIdAndPeriodoAcademico(
                                        post.getEstudiante().getId(),
                                        asignadoVacante.getEmpresa().getId(),
                                        asignadoVacante.getPeriodoAcademico())
                                .stream()
                                .findFirst();
                        if (existente.isPresent()) {
                            return ResponseEntity.ok(existente.get());
                        }
                    }

                    Estudiante estudianteBloqueado = estudianteRepository.findByIdForUpdate(post.getEstudiante().getId())
                            .orElseThrow(() -> new IllegalArgumentException("El estudiante no existe."));
                    boolean practicaActiva = practicaRepository.findByEstudianteId(estudianteBloqueado.getId()).stream()
                            .anyMatch(p -> "pendiente".equals(p.getEstado()) || "en_curso".equals(p.getEstado()));
                    boolean vinculacionActiva = vinculacionRepository.findByEstudianteId(estudianteBloqueado.getId()).stream()
                            .anyMatch(v -> "pendiente".equalsIgnoreCase(v.getEstado()) || "en_curso".equalsIgnoreCase(v.getEstado()));
                    if (practicaActiva || vinculacionActiva) {
                        throw new IllegalArgumentException(
                                "El estudiante ya tiene un proceso académico activo. No puede registrar otra práctica hasta completarlo.");
                    }

                    Practica practica = new Practica();
                    practica.setEstudiante(estudianteBloqueado);
                    practica.setEmpresa(asignadoVacante.getEmpresa());
                    practica.setVacante(asignadoVacante);
                    practica.setTipoAsignacion("MERITOCRACIA");
                    practica.setAsignacionSnapshot(objectMapper.writeValueAsString(
                            snapshotAsignacion(asignadoVacante, "MERITOCRACIA")));
                    practica.setEstado(details != null && details.getEstado() != null ? details.getEstado() : "en_curso");
                    practica.setHorasRequeridas(horasRequeridas(asignadoVacante));
                    if (details != null && details.getHorasCompletadas() != null
                            && details.getHorasCompletadas() != 0) {
                        throw new IllegalArgumentException(
                                "Las horas completadas se calculan desde bitácoras aprobadas y no pueden enviarse manualmente.");
                    }
                    practica.setHorasCompletadas(0);
                    practica.setTutor(tutorAsignacionComponent.exigirValido(
                            details == null ? null : details.getTutor(), PROCESO,
                            asignadoVacante.getEmpresa(), estudianteBloqueado.getCarrera()));
                    if (details != null) {
                        practica.setEncargado(details.getEncargado());
                        practica.setFechaInicio(details.getFechaInicio());
                        practica.setFechaFin(details.getFechaFin());
                    }
                    practica.setPeriodoAcademico(asignadoVacante.getPeriodoAcademico());
                    Practica consolidada = practicaRepository.save(practica);
                    String empresa = asignadoVacante.getEmpresa() == null
                            ? "la empresa asignada"
                            : asignadoVacante.getEmpresa().getNombre();
                    notificacionEmitter.emitir(
                            post.getEstudiante() == null ? null : post.getEstudiante().getUsuario(),
                            "asignacion_practica",
                            "Práctica asignada",
                            "Tu postulación fue consolidada: se registró tu práctica en " + empresa + ".",
                            "practica", consolidada.getId().longValue(), "/dashboard/practicas");
                    notificacionEmitter.emitir(
                            consolidada.getTutor(),
                            "asignacion_practica",
                            "Nueva tutoría de práctica",
                            "Se te asignó un nuevo expediente de práctica en " + empresa + ".",
                            "practica", consolidada.getId().longValue(), "/dashboard/practicas");
                    return ResponseEntity.ok(consolidada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> snapshotAsignacion(VacantePractica vacante, String tipoAsignacion) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("tipoAsignacion", tipoAsignacion);
        snapshot.put("vacanteId", vacante.getId());
        snapshot.put("vacante", vacante.getNombre());
        snapshot.put("empresaId", vacante.getEmpresa() == null ? null : vacante.getEmpresa().getId());
        snapshot.put("empresa", vacante.getEmpresa() == null ? null : vacante.getEmpresa().getNombre());
        snapshot.put("area", vacante.getArea());
        snapshot.put("descripcion", vacante.getDescripcion());
        snapshot.put("modalidadAcademica", vacante.getModalidadAcademica());
        snapshot.put("modalidadTrabajo", vacante.getModalidadTrabajo());
        snapshot.put("horas", vacante.getHoras());
        snapshot.put("periodoAcademico", vacante.getPeriodoAcademico());
        return snapshot;
    }

    private VacantePractica resolverVacanteAsignada(PostulacionMeritocratica post,
                                                    PostulacionMeritocratica details) {
        if (details != null && details.getAsignadoVacante() != null
                && details.getAsignadoVacante().getId() != null) {
            return vacantePracticaRepository.findByIdForUpdate(details.getAsignadoVacante().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacante asignada no encontrada."));
        }

        Empresa empresa = details != null && details.getAsignadoEmpresa() != null
                ? details.getAsignadoEmpresa()
                : post.getAsignadoEmpresa();
        if (empresa == null || empresa.getId() == null) {
            throw new IllegalArgumentException("Debe indicar la vacante asignada antes de aprobar la postulación.");
        }

        List<VacantePractica> candidatas = java.util.stream.Stream.of(post.getPref1(), post.getPref2(), post.getPref3())
                .filter(v -> v != null && v.getEmpresa() != null && empresa.getId().equals(v.getEmpresa().getId()))
                .toList();
        if (candidatas.size() != 1) {
            throw new IllegalArgumentException(
                    "Debe indicar una vacante asignada exacta para descontar cupos sin ambigüedad.");
        }
        return vacantePracticaRepository.findByIdForUpdate(candidatas.get(0).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacante asignada no encontrada."));
    }

    private void descontarCupoVacante(VacantePractica vacante) {
        if (vacante == null || vacante.getId() == null) {
            throw new IllegalArgumentException("Debe indicar la vacante asignada para descontar cupos.");
        }
        if (Boolean.FALSE.equals(vacante.getActiva())) {
            throw new IllegalArgumentException("La vacante está pausada y no admite nuevas asignaciones.");
        }
        // Fase 42: un periodo cerrado bloquea el consumo de cupos
        periodoComponent.exigirActivo(vacante.getPeriodoAcademico());
        int cupos = vacante.getCupos() == null ? 0 : vacante.getCupos();
        if (cupos <= 0) {
            throw new IllegalArgumentException("La vacante asignada no tiene cupos disponibles.");
        }
        vacante.setCupos(cupos - 1);
        vacantePracticaRepository.save(vacante);
    }

    private Integer horasRequeridas(VacantePractica vacante) {
        if (vacante.getHoras() != null && vacante.getHoras() > 0) {
            return vacante.getHoras();
        }
        throw new IllegalArgumentException("La vacante asignada debe definir horas requeridas mayores a cero.");
    }

    private void validarPreferencias(PostulacionMeritocratica postulacion) {
        if (postulacion.getPref1() == null || postulacion.getPref1().getId() == null
                || postulacion.getPref2() == null || postulacion.getPref2().getId() == null) {
            throw new IllegalArgumentException("Debes seleccionar al menos dos preferencias.");
        }
        Set<Integer> ids = java.util.stream.Stream
                .of(postulacion.getPref1(), postulacion.getPref2(), postulacion.getPref3())
                .filter(v -> v != null && v.getId() != null)
                .map(VacantePractica::getId)
                .collect(Collectors.toSet());
        int cantidad = postulacion.getPref3() == null || postulacion.getPref3().getId() == null ? 2 : 3;
        if (ids.size() != cantidad) {
            throw new IllegalArgumentException("No puedes repetir preferencias en la postulación.");
        }
    }

    private void validarVacantesParaEstudiante(PostulacionMeritocratica postulacion,
                                               Estudiante estudiante,
                                               Authentication authentication) {
        VacantePractica pref1 = cargarVacante(postulacion.getPref1().getId());
        VacantePractica pref2 = cargarVacante(postulacion.getPref2().getId());
        VacantePractica pref3 = postulacion.getPref3() != null && postulacion.getPref3().getId() != null
                ? cargarVacante(postulacion.getPref3().getId())
                : null;

        for (VacantePractica vacante : java.util.stream.Stream.of(pref1, pref2, pref3).filter(v -> v != null).toList()) {
            validarVacanteCompatible(vacante, estudiante);
            if (!hasRole(authentication, "ESTUDIANTE")) {
                verificarPuedeGestionarVacante(vacante, authentication);
            }
            int cupos = vacante.getCupos() == null ? 0 : vacante.getCupos();
            if (cupos <= 0) {
                throw new IllegalArgumentException("La vacante '" + vacante.getNombre() + "' no tiene cupos disponibles.");
            }
        }

        postulacion.setPref1(pref1);
        postulacion.setPref2(pref2);
        postulacion.setPref3(pref3);
    }

    private VacantePractica cargarVacante(Integer vacanteId) {
        return vacantePracticaRepository.findById(vacanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacante no encontrada."));
    }

    private void validarVacanteCompatible(VacantePractica vacante, Estudiante estudiante) {
        if (vacante == null) {
            throw new IllegalArgumentException("La vacante no es válida.");
        }
        // Fase 42: una vacante pausada no admite postulaciones ni asignaciones
        if (Boolean.FALSE.equals(vacante.getActiva())) {
            throw new IllegalArgumentException(
                    "La vacante '" + vacante.getNombre() + "' está pausada y no admite postulaciones.");
        }
        if (estudiante == null) {
            throw new IllegalArgumentException("La postulación debe estar asociada a un estudiante.");
        }
        if (!OfertaCuposEmpresaComponent.mismaCarrera(vacante.getCarrera(), estudiante.getCarrera())) {
            throw new IllegalArgumentException("La vacante '" + vacante.getNombre() + "' no corresponde a la carrera del estudiante.");
        }
        if (vacante.getPeriodoAcademico() == null
                || estudiante.getPeriodoAcademico() == null
                || !vacante.getPeriodoAcademico().equals(estudiante.getPeriodoAcademico())) {
            throw new IllegalArgumentException("La vacante '" + vacante.getNombre()
                    + "' no corresponde al periodo académico del estudiante.");
        }
        String etapa = etapaAcademica(estudiante);
        validarSemestreParaEtapa(estudiante, etapa);
        validarPeriodoReintento(estudiante.getId(), vacante.getPeriodoAcademico());
        if (vacante.getModalidadAcademica() == null || !vacante.getModalidadAcademica().equals(etapa)) {
            throw new IllegalArgumentException("La vacante '" + vacante.getNombre() + "' corresponde a " + vacante.getModalidadAcademica()
                    + " y el estudiante está en etapa " + etapa + ".");
        }
    }

    private String etapaAcademica(Estudiante estudiante) {
        boolean vinculacionCompleta = vinculacionRepository.findByEstudianteId(estudiante.getId()).stream()
                .anyMatch(v -> "completado".equalsIgnoreCase(v.getEstado()));
        if (!vinculacionCompleta) {
            return "Vinculación";
        }
        long practicasCompletadas = practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                .filter(p -> "completado".equalsIgnoreCase(p.getEstado()))
                .count();
        if (practicasCompletadas >= 2) {
            throw new IllegalArgumentException(
                    "El estudiante ya completó Práctica I y Práctica II; no puede presentar otra postulación de prácticas.");
        }
        return practicasCompletadas == 0 ? "Práctica I" : "Práctica II";
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

    private void validarSinProcesoActivo(Integer estudianteId) {
        boolean practicaActiva = practicaRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(p -> "pendiente".equalsIgnoreCase(p.getEstado()) || "en_curso".equalsIgnoreCase(p.getEstado()));
        boolean vinculacionActiva = vinculacionRepository.findByEstudianteId(estudianteId).stream()
                .anyMatch(v -> "pendiente".equalsIgnoreCase(v.getEstado()) || "en_curso".equalsIgnoreCase(v.getEstado()));
        if (practicaActiva || vinculacionActiva) {
            throw new IllegalArgumentException("El estudiante ya tiene un proceso académico activo.");
        }
    }

    private String tipoConvocatoriaParaEtapa(String etapa) {
        return "Vinculación".equals(etapa) ? "VINCULACION" : "PRACTICAS";
    }

    private void validarSemestreParaEtapa(Estudiante estudiante, String etapa) {
        if ("Vinculación".equals(etapa)
                && estudiante.getSemestre() != null
                && estudiante.getSemestre() < SEMESTRE_MINIMO_VINCULACION) {
            throw new IllegalArgumentException("Vinculación inicia desde cuarto semestre.");
        }
    }

    private void validarDocumentosObligatorios(Integer estudianteId) {
        Set<String> docs = documentoRepository.findByEstudianteIdAndProceso(estudianteId, "PRACTICAS").stream()
                .filter(doc -> doc.getUrlArchivo() != null && !doc.getUrlArchivo().isBlank())
                .filter(doc -> "aprobado".equals(doc.getEstado()))
                .map(DocEstudiante::getTipoDocumento)
                .collect(Collectors.toSet());
        if (!docs.containsAll(Set.of("cv", "carta", "cedula"))) {
            throw new IllegalArgumentException(
                    "Tus documentos obligatorios de practicas deben estar aprobados antes de postular.");
        }
    }

    private double calcularPromedioAcademicoAnterior(Estudiante estudiante) {
        if (estudiante.getSemestre() == null || estudiante.getSemestre() <= 1) {
            throw new IllegalArgumentException(
                    "El estudiante no tiene semestre actual suficiente para calcular meritocracia.");
        }
        int semestreAnterior = estudiante.getSemestre() - 1;
        return notaAcademicaRepository
                .findFirstByEstudianteIdAndSemestreAndEstadoOrderByPeriodoAcademicoDesc(
                        estudiante.getId(), semestreAnterior, "VERIFICADO")
                .map(nota -> redondear2(nota.getPromedio()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una nota académica verificada del semestre anterior para calcular la meritocracia."));
    }

    private double calcularScore(double promedioOficial) {
        return redondear2(Math.min(10.0, promedioOficial * 0.7 + 3.0));
    }

    private double redondear2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private VacantePractica primeraVacanteConCupo(PostulacionMeritocratica post,
                                                  Map<Integer, Integer> cuposSimulados,
                                                  Authentication authentication) {
        for (VacantePractica pref : java.util.stream.Stream.of(post.getPref1(), post.getPref2(), post.getPref3())
                .filter(v -> v != null)
                .toList()) {
            VacantePractica vacante = cargarVacante(pref.getId());
            if (!puedeGestionarVacante(vacante, authentication)) continue;
            try {
                validarVacanteCompatible(vacante, post.getEstudiante());
                convenioVigenteComponent.exigirParaEmpresa(
                        vacante.getEmpresa(), post.getEstudiante().getCarrera());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (cuposDisponiblesSimulados(vacante, cuposSimulados) > 0) {
                return vacante;
            }
        }
        return null;
    }

    private int cuposDisponiblesSimulados(VacantePractica vacante, Map<Integer, Integer> cuposSimulados) {
        return cuposSimulados.computeIfAbsent(vacante.getId(), id -> vacante.getCupos() == null ? 0 : vacante.getCupos());
    }

    private void validarJustificacionAjuste(PostulacionMeritocratica postulacion) {
        if (postulacion.getJustificacionAjuste() == null || postulacion.getJustificacionAjuste().isBlank()) {
            throw new IllegalArgumentException("El ajuste manual requiere una justificación.");
        }
    }

    void registrarAuditoriaAjuste(PostulacionMeritocratica post,
                                  VacantePractica vacante,
                                  Usuario usuario) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("postulacionId", post.getId());
        datos.put("vacanteId", vacante == null ? null : vacante.getId());
        datos.put("vacante", vacante == null ? "Vacante no persistida" : vacante.getNombre());
        datos.put("justificacion", post.getJustificacionAjuste());
        auditoriaEmitter.registrar("POSTULACIONES_MERITOCRATICAS", "AJUSTE_MANUAL_ASIGNACION",
                usuario, null, datos);
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
        return alcanceCoordinador.procesoVisible(authentication, PROCESO)
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private boolean puedeGestionarEstudiante(Estudiante estudiante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return true;
        return hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante);
    }

    private void verificarPuedeGestionarEstudiante(Integer estudianteId, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar postulaciones de este estudiante.");
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

    private boolean puedeGestionarVacante(VacantePractica vacante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return true;
        if (hasRole(authentication, "COORDINADOR")) {
            return alcanceCoordinador.procesoVisible(authentication, PROCESO)
                    && alcanceCoordinador.carrerasVisibles(authentication)
                    .map(carreras -> vacante != null && carreras.contains(vacante.getCarrera()))
                    .orElse(false);
        }
        return false;
    }
}
