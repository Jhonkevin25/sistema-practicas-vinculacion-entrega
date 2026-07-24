package ec.edu.unibe.sistema_practicas.evaluacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.bitacora.FlujoBitacora;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.configuracion.FechaLimiteCalificacionRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/evaluaciones")
@RequiredArgsConstructor
public class EvaluacionPracticaDetalleController {

    private static final String PRACTICAS = "PRACTICAS";
    private static final String VINCULACION = "VINCULACION";

    private final EvaluacionPracticaDetalleRepository evaluacionRepository;
    private final EncuestaSatisfaccionRepository encuestaRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final FechaLimiteCalificacionRepository fechaLimiteRepository;
    private final BitacoraRepository bitacoraRepository;
    private final NotificacionEmitter notificacionEmitter;
    private final CierreExpedienteComponent cierreExpedienteComponent;
    private final AuditoriaEmitter auditoriaEmitter;

    @GetMapping
    public List<EvaluacionPracticaDetalle> getAll(Authentication authentication,
                                                  @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(estudiante -> evaluacionesDeEstudiante(estudiante.getId()))
                    .orElse(List.of());
        }
        if (hasRole(authentication, "TUTOR")) {
            return usuario == null ? List.of() : evaluacionesDelTutor(usuario.getId());
        }
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> evaluacionesDeCoordinacion(authentication, carreras))
                .orElseGet(evaluacionRepository::findAll);
    }

    private List<EvaluacionPracticaDetalle> evaluacionesDeEstudiante(Integer estudianteId) {
        Map<Integer, EvaluacionPracticaDetalle> evaluaciones = new LinkedHashMap<>();
        evaluacionRepository.findByPracticaEstudianteId(estudianteId)
                .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        evaluacionRepository.findByVinculacionEstudianteId(estudianteId)
                .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        return evaluaciones.values().stream().toList();
    }

    private List<EvaluacionPracticaDetalle> evaluacionesDelTutor(Integer tutorId) {
        Map<Integer, EvaluacionPracticaDetalle> evaluaciones = new LinkedHashMap<>();
        evaluacionRepository.findByPracticaTutorId(tutorId)
                .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        evaluacionRepository.findByVinculacionTutorId(tutorId)
                .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        return evaluaciones.values().stream().toList();
    }

    private List<EvaluacionPracticaDetalle> evaluacionesDeCoordinacion(
            Authentication authentication, Set<String> carreras) {
        if (carreras.isEmpty()) return List.of();
        Map<Integer, EvaluacionPracticaDetalle> evaluaciones = new LinkedHashMap<>();
        if (alcanceCoordinador.procesoVisible(authentication, PRACTICAS)) {
            evaluacionRepository.findByPracticaEstudianteCarreraIn(carreras)
                    .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        }
        if (alcanceCoordinador.procesoVisible(authentication, VINCULACION)) {
            evaluacionRepository.findByVinculacionEstudianteCarreraIn(carreras)
                    .forEach(evaluacion -> evaluaciones.put(evaluacion.getId(), evaluacion));
        }
        return evaluaciones.values().stream().toList();
    }

    @GetMapping("/practica/{practicaId}")
    public List<EvaluacionPracticaDetalle> getByPractica(@PathVariable Integer practicaId,
                                                         Authentication authentication,
                                                         @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(practicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVerPractica(practica, authentication, usuario);
        return evaluacionRepository.findByPracticaId(practicaId);
    }

    @GetMapping("/vinculacion/{vinculacionId}")
    public List<EvaluacionPracticaDetalle> getByVinculacion(@PathVariable Integer vinculacionId,
                                                            Authentication authentication,
                                                            @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(vinculacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVerVinculacion(vinculacion, authentication, usuario);
        return evaluacionRepository.findByVinculacionId(vinculacionId);
    }

    @GetMapping("/encuesta/practica/{practicaId}")
    public List<EncuestaSatisfaccion> getEncuestasPractica(@PathVariable Integer practicaId,
                                                           Authentication authentication,
                                                           @AuthenticationPrincipal Usuario usuario) {
        Practica practica = practicaRepository.findById(practicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
        verificarPuedeVerPractica(practica, authentication, usuario);
        return encuestaRepository.findByPracticaId(practicaId);
    }

    @GetMapping("/encuesta/vinculacion/{vinculacionId}")
    public List<EncuestaSatisfaccion> getEncuestasVinculacion(@PathVariable Integer vinculacionId,
                                                              Authentication authentication,
                                                              @AuthenticationPrincipal Usuario usuario) {
        Vinculacion vinculacion = vinculacionRepository.findById(vinculacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
        verificarPuedeVerVinculacion(vinculacion, authentication, usuario);
        return encuestaRepository.findByVinculacionId(vinculacionId);
    }

    // Regla: valida rango de nota 0-10
    private void validarRango(Double nota, String etiqueta) {
        if (nota != null && (nota < 0 || nota > 10)) {
            throw new IllegalArgumentException("La " + etiqueta + " debe estar entre 0 y 10.");
        }
    }

    // Regla: los parciales se califican en orden; el parcial N requiere que
    // el N-1 este cerrado: notas, encuesta y bitacoras revisadas.
    private void validarSecuencia(Integer practicaId, Integer vinculacionId, Integer parcial) {
        if (parcial == null || parcial <= 1) return;
        if (!parcialCerrado(practicaId, vinculacionId, parcial - 1)) {
            throw new IllegalArgumentException(
                "No puedes calificar el Parcial " + parcial + ": el Parcial " + (parcial - 1)
                + " aún no está cerrado con notas, encuesta y bitácoras revisadas.");
        }
    }

    private boolean parcialCerrado(Integer practicaId, Integer vinculacionId, Integer parcial) {
        Optional<EvaluacionPracticaDetalle> evaluacion = practicaId != null
                ? evaluacionRepository.findByPracticaIdAndParcial(practicaId, parcial)
                : evaluacionRepository.findByVinculacionIdAndParcial(vinculacionId, parcial);
        boolean notasYEncuesta = evaluacion
                .map(prev -> prev.getNotaTutor() != null && prev.getNotaTutor() > 0
                          && prev.getNotaCoord() != null && prev.getNotaCoord() > 0
                          && Boolean.TRUE.equals(prev.getEncuestaCompletada()))
                .orElse(false);
        boolean bitacorasPendientes = practicaId != null
                ? FlujoBitacora.tieneAbiertas(bitacoraRepository.findByPracticaId(practicaId), parcial)
                : FlujoBitacora.tieneAbiertas(bitacoraRepository.findByVinculacionId(vinculacionId), parcial);
        return notasYEncuesta && !bitacorasPendientes;
    }

    // Upsert por (practica, parcial): tutor y coordinador guardan cada uno su
    // nota; la nota final se recalcula como promedio ponderado 50/50
    @PostMapping
    @Transactional
    public EvaluacionPracticaDetalle upsert(@Valid @RequestBody EvaluacionPracticaDetalle detalle,
                                            Authentication authentication,
                                            @AuthenticationPrincipal Usuario usuario,
                                            @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        boolean tienePractica = detalle.getPractica() != null && detalle.getPractica().getId() != null;
        boolean tieneVinculacion = detalle.getVinculacion() != null && detalle.getVinculacion().getId() != null;
        if (tienePractica == tieneVinculacion) {
            throw new IllegalArgumentException("La evaluación debe asociarse a una práctica o una vinculación, no a ambas.");
        }

        Practica practica = null;
        Vinculacion vinculacion = null;
        Integer practicaId = null;
        Integer vinculacionId = null;
        if (tienePractica) {
            practicaId = detalle.getPractica().getId();
            practica = practicaRepository.findById(practicaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            verificarPuedeCalificarPractica(practica, authentication, usuario);
            cierreExpedienteComponent.validarModificacionPermitida(
                    practica, authentication, usuario, justificacionAdmin, "EVALUACIONES_PRACTICAS_DETALLE", null);
        } else {
            vinculacionId = detalle.getVinculacion().getId();
            vinculacion = vinculacionRepository.findById(vinculacionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            verificarPuedeCalificarVinculacion(vinculacion, authentication, usuario);
            cierreExpedienteComponent.validarModificacionPermitida(
                    vinculacion, authentication, usuario, justificacionAdmin, "EVALUACIONES_PRACTICAS_DETALLE", null);
        }
        validarNotaPermitidaPorRol(detalle, authentication);
        validarRango(detalle.getNotaTutor(), "nota del tutor");
        validarRango(detalle.getNotaCoord(), "nota del coordinador");
        validarSecuencia(practicaId, vinculacionId, detalle.getParcial());
        validarFechaLimite(tienePractica ? practica.getPeriodoAcademico() : vinculacion.getPeriodoAcademico(),
                detalle.getParcial(), authentication);

        EvaluacionPracticaDetalle target = (tienePractica
                ? evaluacionRepository.findByPracticaIdAndParcial(practicaId, detalle.getParcial())
                : evaluacionRepository.findByVinculacionIdAndParcial(vinculacionId, detalle.getParcial()))
                .orElse(detalle);
        boolean evaluacionExistente = target.getId() != null;
        Map<String, Object> auditoriaAntes = evaluacionExistente ? snapshotEvaluacion(target) : null;
        boolean notasCompletasAntes = target.getId() != null
                && notaRegistrada(target.getNotaTutor())
                && notaRegistrada(target.getNotaCoord());
        Double notaTutorAntes = target.getId() == null ? null : target.getNotaTutor();
        Double notaCoordAntes = target.getId() == null ? null : target.getNotaCoord();
        target.setPractica(practica);
        target.setVinculacion(vinculacion);
        target.setParcial(detalle.getParcial());

        if (target.getId() != null) {
            if (detalle.getNotaTutor() != null) {
                target.setNotaTutor(detalle.getNotaTutor());
            }
            if (detalle.getNotaCoord() != null) {
                target.setNotaCoord(detalle.getNotaCoord());
            }
            if (detalle.getEncuestaCompletada() != null) {
                target.setEncuestaCompletada(detalle.getEncuestaCompletada());
            }
        }

        // Normalizar campos no enviados y recalcular la nota final 50/50
        if (target.getNotaTutor() == null) target.setNotaTutor(0.0);
        if (target.getNotaCoord() == null) target.setNotaCoord(0.0);
        if (target.getEncuestaCompletada() == null) target.setEncuestaCompletada(false);
        target.setNotaFinal(target.getNotaTutor() * 0.5 + target.getNotaCoord() * 0.5);

        EvaluacionPracticaDetalle guardado = evaluacionRepository.save(target);
        Usuario destinatario = tienePractica
                ? (practica.getEstudiante() == null ? null : practica.getEstudiante().getUsuario())
                : (vinculacion.getEstudiante() == null ? null : vinculacion.getEstudiante().getUsuario());
        String tipoExpediente = tienePractica ? "práctica" : "vinculación";
        String ruta = tienePractica ? "/dashboard/practicas" : "/dashboard/vinculacion";
        boolean notaCambio = (detalle.getNotaTutor() != null && !detalle.getNotaTutor().equals(notaTutorAntes))
                || (detalle.getNotaCoord() != null && !detalle.getNotaCoord().equals(notaCoordAntes));
        if (notaCambio) {
            auditoriaEmitter.registrar("EVALUACIONES_PRACTICAS_DETALLE",
                    evaluacionExistente ? "MODIFICAR_NOTA" : "REGISTRAR_NOTA",
                    usuario, auditoriaAntes, snapshotEvaluacion(guardado));
            notificacionEmitter.emitir(destinatario, "nota_registrada",
                    "Nota registrada",
                    "Se registró una nota del parcial " + guardado.getParcial() + " de tu " + tipoExpediente + ".",
                    "evaluacion", guardado.getId().longValue(), ruta);
        }
        boolean notasCompletasAhora = notaRegistrada(guardado.getNotaTutor())
                && notaRegistrada(guardado.getNotaCoord());
        if (!notasCompletasAntes && notasCompletasAhora
                && !Boolean.TRUE.equals(guardado.getEncuestaCompletada())) {
            notificacionEmitter.emitir(destinatario, "encuesta_habilitada",
                    "Encuesta habilitada",
                    "Ya puedes completar la encuesta de satisfacción del parcial " + guardado.getParcial() + ".",
                    "evaluacion", guardado.getId().longValue(), ruta);
        }
        return guardado;
    }

    private Map<String, Object> snapshotEvaluacion(EvaluacionPracticaDetalle detalle) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", detalle.getId());
        datos.put("practicaId", detalle.getPractica() == null ? null : detalle.getPractica().getId());
        datos.put("vinculacionId", detalle.getVinculacion() == null ? null : detalle.getVinculacion().getId());
        datos.put("parcial", detalle.getParcial());
        datos.put("notaTutor", detalle.getNotaTutor());
        datos.put("notaCoordinador", detalle.getNotaCoord());
        datos.put("notaFinal", detalle.getNotaFinal());
        return datos;
    }

    private boolean notaRegistrada(Double nota) {
        return nota != null && nota > 0;
    }

    // El estudiante solo puede marcar su encuesta de pertinencia como completada
    // para una practica propia.
    // Sin @Valid en el cuerpo: el parcial llega como query param y se asigna
    // despues del binding (el @NotNull de la entidad fallaria siempre);
    // validarEncuesta() cubre las preguntas obligatorias y su rango.
    @PostMapping("/encuesta")
    public ResponseEntity<EvaluacionPracticaDetalle> marcarEncuesta(
            @RequestParam(required = false) Integer practicaId,
            @RequestParam(required = false) Integer vinculacionId,
            @RequestParam Integer parcial,
            @RequestBody(required = false) EncuestaSatisfaccion encuesta,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        boolean tienePractica = practicaId != null;
        boolean tieneVinculacion = vinculacionId != null;
        if (tienePractica == tieneVinculacion) {
            throw new IllegalArgumentException("La encuesta debe asociarse a una práctica o una vinculación, no a ambas.");
        }

        Practica practica = null;
        Vinculacion vinculacion = null;
        EvaluacionPracticaDetalle target;
        boolean encuestaExistente;
        if (tienePractica) {
            practica = practicaRepository.findById(practicaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            verificarPracticaPropiaDelEstudiante(practica, authentication, usuario);
            cierreExpedienteComponent.validarModificacionPermitida(
                    practica, authentication, usuario, null, "ENCUESTAS_SATISFACCION", null);
            encuestaExistente = encuestaRepository.existsByPracticaIdAndParcial(practicaId, parcial);
            target = evaluacionRepository.findByPracticaIdAndParcial(practicaId, parcial)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La encuesta se habilita cuando existen nota de tutor y nota de coordinador."));
        } else {
            vinculacion = vinculacionRepository.findById(vinculacionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            verificarVinculacionPropiaDelEstudiante(vinculacion, authentication, usuario);
            cierreExpedienteComponent.validarModificacionPermitida(
                    vinculacion, authentication, usuario, null, "ENCUESTAS_SATISFACCION", null);
            encuestaExistente = encuestaRepository.existsByVinculacionIdAndParcial(vinculacionId, parcial);
            target = evaluacionRepository.findByVinculacionIdAndParcial(vinculacionId, parcial)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La encuesta se habilita cuando existen nota de tutor y nota de coordinador."));
        }

        if (encuestaExistente) {
            throw new IllegalArgumentException("La encuesta de este parcial ya fue registrada.");
        }
        if (target.getNotaTutor() == null || target.getNotaTutor() <= 0
                || target.getNotaCoord() == null || target.getNotaCoord() <= 0) {
            throw new IllegalArgumentException(
                    "La encuesta se habilita cuando existen nota de tutor y nota de coordinador.");
        }

        EncuestaSatisfaccion nueva = encuesta != null ? encuesta : new EncuestaSatisfaccion();
        nueva.setEstudiante(tienePractica ? practica.getEstudiante() : vinculacion.getEstudiante());
        nueva.setPractica(practica);
        nueva.setVinculacion(vinculacion);
        nueva.setParcial(parcial);
        validarEncuesta(nueva);
        nueva.setFechaEnvio(LocalDateTime.now());
        try {
            encuestaRepository.save(nueva);
        } catch (DataIntegrityViolationException e) {
            // Doble envio casi simultaneo (doble clic, dos pestañas): la
            // verificacion previa de encuestaExistente no alcanzo a verlo.
            throw new IllegalArgumentException("La encuesta de este parcial ya fue registrada.");
        }

        target.setEncuestaCompletada(true);
        if (target.getNotaTutor() == null) target.setNotaTutor(0.0);
        if (target.getNotaCoord() == null) target.setNotaCoord(0.0);
        if (target.getNotaFinal() == null) target.setNotaFinal(0.0);
        return ResponseEntity.ok(evaluacionRepository.save(target));
    }

    private void validarNotaPermitidaPorRol(EvaluacionPracticaDetalle detalle, Authentication authentication) {
        if (hasRole(authentication, "TUTOR") && detalle.getNotaCoord() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El tutor no puede registrar nota del coordinador.");
        }
        if (hasRole(authentication, "COORDINADOR") && detalle.getNotaTutor() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El coordinador no puede registrar nota del tutor.");
        }
    }

    private void validarFechaLimite(String periodoAcademico, Integer parcial, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (periodoAcademico == null || parcial == null) return;
        fechaLimiteRepository.findByPeriodoAcademicoAndParcial(periodoAcademico, parcial)
                .ifPresent(limite -> {
                    if (LocalDate.now().isAfter(limite.getFechaLimite())) {
                        throw new IllegalArgumentException(
                                "El periodo de calificación del Parcial " + parcial + " venció el "
                                        + limite.getFechaLimite() + ".");
                    }
                });
    }

    private void validarEncuesta(EncuestaSatisfaccion encuesta) {
        if (encuesta.getSatisfaccionTutor() == null
                || encuesta.getSatisfaccionEmpresaProyecto() == null
                || encuesta.getRelacionCarrera() == null
                || encuesta.getClaridadInstrucciones() == null) {
            throw new IllegalArgumentException("La encuesta debe completar todas las preguntas obligatorias.");
        }
        if (!entre1y5(encuesta.getSatisfaccionTutor())
                || !entre1y5(encuesta.getSatisfaccionEmpresaProyecto())
                || !entre1y5(encuesta.getRelacionCarrera())
                || !entre1y5(encuesta.getClaridadInstrucciones())) {
            throw new IllegalArgumentException("Las respuestas de la encuesta deben estar entre 1 y 5.");
        }
    }

    private boolean entre1y5(Integer valor) {
        return valor != null && valor >= 1 && valor <= 5;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante, String proceso) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.procesoVisible(authentication, proceso)
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> estudiante != null && carreras.contains(estudiante.getCarrera()))
                .orElse(false);
    }

    private boolean puedeVerPractica(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return true;
        if (hasRole(authentication, "COORDINADOR")
                && carreraVisible(authentication, practica.getEstudiante(), PRACTICAS)) return true;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return true;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = estudianteActual(usuario);
            return estudiante.isPresent()
                    && practica.getEstudiante() != null
                    && estudiante.get().getId().equals(practica.getEstudiante().getId());
        }
        return false;
    }

    private boolean puedeVerVinculacion(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return true;
        if (hasRole(authentication, "COORDINADOR")
                && carreraVisible(authentication, vinculacion.getEstudiante(), VINCULACION)) return true;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return true;
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = estudianteActual(usuario);
            return estudiante.isPresent()
                    && vinculacion.getEstudiante() != null
                    && estudiante.get().getId().equals(vinculacion.getEstudiante().getId());
        }
        return false;
    }

    private void verificarPuedeVerPractica(Practica practica, Authentication authentication, Usuario usuario) {
        if (puedeVerPractica(practica, authentication, usuario)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las evaluaciones de esta práctica.");
    }

    private void verificarPuedeVerVinculacion(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (puedeVerVinculacion(vinculacion, authentication, usuario)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las evaluaciones de esta vinculación.");
    }

    private void verificarPuedeCalificarPractica(Practica practica, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && carreraVisible(authentication, practica.getEstudiante(), PRACTICAS)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && practica.getTutor() != null
                && usuario.getId().equals(practica.getTutor().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes calificar esta práctica.");
    }

    private void verificarPuedeCalificarVinculacion(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && carreraVisible(authentication, vinculacion.getEstudiante(), VINCULACION)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && vinculacion.getTutor() != null
                && usuario.getId().equals(vinculacion.getTutor().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes calificar esta vinculación.");
    }

    private void verificarPracticaPropiaDelEstudiante(Practica practica, Authentication authentication, Usuario usuario) {
        if (!hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el estudiante puede marcar su encuesta.");
        }
        Optional<Estudiante> estudiante = estudianteActual(usuario);
        if (estudiante.isPresent()
                && practica.getEstudiante() != null
                && estudiante.get().getId().equals(practica.getEstudiante().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes marcar la encuesta de una práctica ajena.");
    }

    private void verificarVinculacionPropiaDelEstudiante(Vinculacion vinculacion, Authentication authentication, Usuario usuario) {
        if (!hasRole(authentication, "ESTUDIANTE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el estudiante puede marcar su encuesta.");
        }
        Optional<Estudiante> estudiante = estudianteActual(usuario);
        if (estudiante.isPresent()
                && vinculacion.getEstudiante() != null
                && estudiante.get().getId().equals(vinculacion.getEstudiante().getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes marcar la encuesta de una vinculación ajena.");
    }
}
