package ec.edu.unibe.sistema_practicas.bitacora;

import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/bitacoras")
@RequiredArgsConstructor
public class BitacoraController {

    private static final String PRACTICAS = "PRACTICAS";
    private static final String VINCULACION = "VINCULACION";
    private static final ZoneId ZONA_ECUADOR = ZoneId.of("America/Guayaquil");

    private final BitacoraRepository bitacoraRepository;
    private final EstudianteRepository estudianteRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;
    private final CierreExpedienteComponent cierreExpedienteComponent;
    private final HorasExpedienteComponent horasExpedienteComponent;
    private final AsistenciaRepository asistenciaRepository;
    private final CoordinadorCarreraRepository coordinadorCarreraRepository;

    @GetMapping
    public List<Bitacora> getAll(Authentication authentication,
                                 @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "ESTUDIANTE")) {
            return estudianteActual(usuario)
                    .map(est -> bitacoraRepository.findByEstudianteId(est.getId()))
                    .orElse(List.of());
        }
        if (hasRole(authentication, "TUTOR")) {
            return usuario == null ? List.of() : bitacorasDelTutor(usuario.getId());
        }
        List<Bitacora> visibles = alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<Bitacora>of()
                        : bitacoraRepository.findByEstudianteCarreraIn(carreras))
                .orElseGet(bitacoraRepository::findAll);
        return visibles.stream()
                .filter(b -> procesoVisible(authentication, b))
                .toList();
    }

    @GetMapping("/me")
    public List<Bitacora> getMisBitacoras(@AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> bitacoraRepository.findByEstudianteId(est.getId()))
                .orElse(List.of());
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<Bitacora> getByEstudiante(@PathVariable Integer estudianteId,
                                          Authentication authentication,
                                          @AuthenticationPrincipal Usuario usuario) {
        verificarPuedeVerEstudiante(estudianteId, authentication, usuario);
        return bitacoraRepository.findByEstudianteId(estudianteId).stream()
                .filter(bitacora -> procesoVisible(authentication, bitacora))
                .toList();
    }

    @PostMapping
    @Transactional
    public Bitacora create(@Valid @RequestBody Bitacora bitacora,
                           Authentication authentication,
                           @AuthenticationPrincipal Usuario usuario,
                           @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        if (hasRole(authentication, "TUTOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El tutor revisa bitácoras, no las registra.");
        }
        if (hasRole(authentication, "ESTUDIANTE")) {
            Estudiante estudiante = estudianteActual(usuario)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tu usuario no tiene un expediente de estudiante asociado."));
            bitacora.setEstudiante(estudiante);
        } else {
            if (bitacora.getEstudiante() == null || bitacora.getEstudiante().getId() == null) {
                throw new IllegalArgumentException("La bitácora debe estar asociada a un estudiante.");
            }
            verificarPuedeGestionarEstudiante(bitacora.getEstudiante().getId(), authentication, usuario);
        }
        validarBitacora(bitacora);
        resolverExpediente(bitacora, authentication, usuario, justificacionAdmin);
        validarSinCorreccionPendiente(bitacora);
        bitacora.setEstado("pendiente");
        bitacora.setObservacionesTutor(null);
        bitacora.setRevisadoPor(null);
        bitacora.setFechaRevision(null);
        bitacora.setUpdatedAt(LocalDateTime.now());
        bitacora.setFechaRegistro(LocalDate.now(ZONA_ECUADOR));
        Bitacora guardada = bitacoraRepository.save(bitacora);
        notificarDiscrepanciasSiAplica(guardada);
        return guardada;
    }

    @PutMapping("/{id}/reenviar")
    @Transactional
    public ResponseEntity<Bitacora> reenviarCorreccion(
            @PathVariable Integer id,
            @Valid @RequestBody ReenvioBitacoraRequest details,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        return bitacoraRepository.findById(id)
                .map(bitacora -> {
                    Estudiante estudiante = estudianteActual(usuario)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Tu usuario no tiene un expediente de estudiante asociado."));
                    if (bitacora.getEstudiante() == null
                            || !estudiante.getId().equals(bitacora.getEstudiante().getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "No puedes corregir una bitácora de otro estudiante.");
                    }
                    if (!"requiere_correccion".equalsIgnoreCase(bitacora.getEstado())) {
                        throw new IllegalArgumentException(
                                "Solo se puede reenviar una bitácora que requiere corrección.");
                    }
                    if (!FlujoBitacora.esCorreccionSinResolver(bitacora, bitacorasDelExpediente(bitacora))) {
                        throw new IllegalArgumentException(
                                "Esta corrección ya fue resuelta por una bitácora aprobada posterior.");
                    }

                    validarBitacoraEditable(bitacora, authentication, usuario, null);
                    validarExpedienteActivo(bitacora);
                    bitacora.setFecha(details.fecha());
                    bitacora.setActividad(details.actividad().trim());
                    bitacora.setHoras(details.horas());
                    bitacora.setObservaciones(details.observaciones());
                    bitacora.setEstado("pendiente");
                    bitacora.setRevisadoPor(null);
                    bitacora.setFechaRevision(null);
                    bitacora.setUpdatedAt(LocalDateTime.now());
                    return ResponseEntity.ok(bitacoraRepository.save(bitacora));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Bitacora> update(@PathVariable Integer id, @RequestBody Bitacora details,
                                           Authentication authentication,
                                           @AuthenticationPrincipal Usuario usuario,
                                           @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        return bitacoraRepository.findById(id)
                .map(bitacora -> {
                    verificarPuedeRevisarBitacora(bitacora, authentication, usuario);
                    validarBitacoraEditable(bitacora, authentication, usuario, justificacionAdmin);
                    String estadoAnterior = bitacora.getEstado();
                    if (details.getEstado() != null) {
                        bitacora.setEstado(normalizarEstado(details.getEstado()));
                    }
                    if (details.getObservacionesTutor() != null) {
                        bitacora.setObservacionesTutor(details.getObservacionesTutor());
                    } else if (details.getObservaciones() != null) {
                        bitacora.setObservacionesTutor(details.getObservaciones());
                    }
                    if ("rechazada".equals(bitacora.getEstado()) || "requiere_correccion".equals(bitacora.getEstado())) {
                        if (bitacora.getObservacionesTutor() == null || bitacora.getObservacionesTutor().isBlank()) {
                            throw new IllegalArgumentException("La revisión con rechazo o corrección requiere observaciones del tutor.");
                        }
                    }
                    bitacora.setRevisadoPor(usuario);
                    bitacora.setFechaRevision(LocalDateTime.now());
                    bitacora.setUpdatedAt(LocalDateTime.now());
                    Bitacora guardada = bitacoraRepository.save(bitacora);
                    recalcularHorasExpediente(guardada);
                    if ("rechazada".equals(guardada.getEstado()) && !"rechazada".equals(estadoAnterior)) {
                        notificacionEmitter.emitir(
                                guardada.getEstudiante() == null ? null : guardada.getEstudiante().getUsuario(),
                                "bitacora_rechazada",
                                "Bitácora rechazada",
                                "Tu bitácora fue rechazada por el tutor. Revisa las observaciones y regístrala nuevamente.",
                                "bitacora", guardada.getId().longValue(),
                                guardada.getVinculacion() != null ? "/dashboard/vinculacion" : "/dashboard/practicas");
                    }
                    if ("requiere_correccion".equals(guardada.getEstado()) && !"requiere_correccion".equals(estadoAnterior)) {
                        notificacionEmitter.emitir(
                                guardada.getEstudiante() == null ? null : guardada.getEstudiante().getUsuario(),
                                "bitacora_requiere_correccion",
                                "Bitácora requiere corrección",
                                "Tu bitácora requiere una corrección. Revisa las observaciones y actualízala.",
                                "bitacora", guardada.getId().longValue(),
                                guardada.getVinculacion() != null ? "/dashboard/vinculacion" : "/dashboard/practicas");
                    }
                    return ResponseEntity.ok(guardada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void validarBitacora(Bitacora bitacora) {
        if (bitacora.getHoras() == null || bitacora.getHoras() < 1 || bitacora.getHoras() > 24) {
            throw new IllegalArgumentException("Las horas de la bitácora deben estar entre 1 y 24.");
        }
        if (bitacora.getParcial() == null || bitacora.getParcial() < 1 || bitacora.getParcial() > 3) {
            throw new IllegalArgumentException("El parcial de la bitácora debe ser 1, 2 o 3.");
        }
        boolean tienePractica = bitacora.getPractica() != null && bitacora.getPractica().getId() != null;
        boolean tieneVinculacion = bitacora.getVinculacion() != null && bitacora.getVinculacion().getId() != null;
        if (tienePractica && tieneVinculacion) {
            throw new IllegalArgumentException("La bitácora debe asociarse a una práctica o una vinculación, no a ambas.");
        }
    }

    private void validarSinCorreccionPendiente(Bitacora bitacora) {
        List<Bitacora> existentes = bitacorasDelExpediente(bitacora);
        if (FlujoBitacora.tieneCorreccionSinResolver(existentes, bitacora.getParcial())) {
            throw new IllegalArgumentException(
                    "Ya existe una bitácora de este parcial que requiere corrección. Edítala y reenvíala.");
        }
    }

    private List<Bitacora> bitacorasDelExpediente(Bitacora bitacora) {
        return bitacora.getPractica() != null
                ? bitacoraRepository.findByPracticaId(bitacora.getPractica().getId())
                : bitacoraRepository.findByVinculacionId(bitacora.getVinculacion().getId());
    }

    private void validarExpedienteActivo(Bitacora bitacora) {
        if (bitacora.getPractica() != null) {
            validarProcesoActivo(bitacora.getPractica().getEstado());
            return;
        }
        if (bitacora.getVinculacion() != null) {
            validarProcesoActivo(bitacora.getVinculacion().getEstado());
            return;
        }
        throw new IllegalArgumentException("La bitácora no está asociada a un expediente activo.");
    }

    private void resolverExpediente(Bitacora bitacora, Authentication authentication,
                                    Usuario usuario, String justificacionAdmin) {
        boolean tienePractica = bitacora.getPractica() != null && bitacora.getPractica().getId() != null;
        boolean tieneVinculacion = bitacora.getVinculacion() != null && bitacora.getVinculacion().getId() != null;
        if (tienePractica) {
            Practica practica = practicaRepository.findById(bitacora.getPractica().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            verificarProceso(authentication, PRACTICAS);
            validarExpedienteDeEstudiante(bitacora.getEstudiante(), practica.getEstudiante());
            cierreExpedienteComponent.validarModificacionPermitida(
                    practica, authentication, usuario, justificacionAdmin, "BITACORAS", bitacora.getId());
            if (!cierreExpedienteComponent.estaCerrada(practica)) {
                validarProcesoActivo(practica.getEstado());
            }
            bitacora.setPractica(practica);
            bitacora.setVinculacion(null);
            return;
        }
        if (tieneVinculacion) {
            Vinculacion vinculacion = vinculacionRepository.findById(bitacora.getVinculacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            verificarProceso(authentication, VINCULACION);
            validarExpedienteDeEstudiante(bitacora.getEstudiante(), vinculacion.getEstudiante());
            cierreExpedienteComponent.validarModificacionPermitida(
                    vinculacion, authentication, usuario, justificacionAdmin, "BITACORAS", bitacora.getId());
            if (!cierreExpedienteComponent.estaCerrada(vinculacion)) {
                validarProcesoActivo(vinculacion.getEstado());
            }
            bitacora.setVinculacion(vinculacion);
            bitacora.setPractica(null);
            return;
        }

        List<Practica> practicasActivas = practicaRepository.findByEstudianteId(bitacora.getEstudiante().getId()).stream()
                .filter(p -> esProcesoActivo(p.getEstado()))
                .toList();
        List<Vinculacion> vinculacionesActivas = vinculacionRepository.findByEstudianteId(bitacora.getEstudiante().getId()).stream()
                .filter(v -> esProcesoActivo(v.getEstado()))
                .toList();
        int total = practicasActivas.size() + vinculacionesActivas.size();
        if (total != 1) {
            throw new IllegalArgumentException("La bitácora requiere una práctica o vinculación activa exacta.");
        }
        if (!practicasActivas.isEmpty()) {
            verificarProceso(authentication, PRACTICAS);
            bitacora.setPractica(practicasActivas.get(0));
            bitacora.setVinculacion(null);
        } else {
            verificarProceso(authentication, VINCULACION);
            bitacora.setVinculacion(vinculacionesActivas.get(0));
            bitacora.setPractica(null);
        }
    }

    private void validarBitacoraEditable(Bitacora bitacora, Authentication authentication,
                                         Usuario usuario, String justificacionAdmin) {
        if (bitacora.getPractica() != null && bitacora.getPractica().getId() != null) {
            cierreExpedienteComponent.validarModificacionPermitida(
                    bitacora.getPractica(), authentication, usuario, justificacionAdmin,
                    "BITACORAS", bitacora.getId());
        }
        if (bitacora.getVinculacion() != null && bitacora.getVinculacion().getId() != null) {
            cierreExpedienteComponent.validarModificacionPermitida(
                    bitacora.getVinculacion(), authentication, usuario, justificacionAdmin,
                    "BITACORAS", bitacora.getId());
        }
    }

    private void validarExpedienteDeEstudiante(Estudiante estudianteBitacora, Estudiante estudianteExpediente) {
        if (estudianteBitacora == null || estudianteBitacora.getId() == null
                || estudianteExpediente == null || !estudianteBitacora.getId().equals(estudianteExpediente.getId())) {
            throw new IllegalArgumentException("La bitácora no corresponde al expediente del estudiante.");
        }
    }

    private void validarProcesoActivo(String estado) {
        if (!esProcesoActivo(estado)) {
            throw new IllegalArgumentException("Solo se pueden registrar bitácoras en expedientes activos.");
        }
    }

    private boolean esProcesoActivo(String estado) {
        return "pendiente".equals(estado) || "en_curso".equals(estado);
    }

    private String normalizarEstado(String estado) {
        if ("aprobado".equals(estado)) return "aprobada";
        if ("rechazado".equals(estado)) return "rechazada";
        if (!Set.of("pendiente", "aprobada", "rechazada", "requiere_correccion").contains(estado)) {
            throw new IllegalArgumentException("Estado de bitácora inválido.");
        }
        return estado;
    }

    private void recalcularHorasExpediente(Bitacora bitacora) {
        if (bitacora.getPractica() != null && bitacora.getPractica().getId() != null) {
            Practica practica = practicaRepository.findById(bitacora.getPractica().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Práctica no encontrada."));
            horasExpedienteComponent.aplicarHorasAprobadas(practica);
            practicaRepository.save(practica);
        }
        if (bitacora.getVinculacion() != null && bitacora.getVinculacion().getId() != null) {
            Vinculacion vinculacion = vinculacionRepository.findById(bitacora.getVinculacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada."));
            horasExpedienteComponent.aplicarHorasAprobadas(vinculacion);
            vinculacionRepository.save(vinculacion);
        }
    }

    private void notificarDiscrepanciasSiAplica(Bitacora bitacora) {
        Asistencia asistenciaDelDia = buscarAsistenciaDelDia(bitacora);
        List<String> discrepancias = detectarDiscrepancias(bitacora, asistenciaDelDia);
        if (discrepancias.isEmpty()) return;

        String nombreEstudiante = bitacora.getEstudiante() == null ? "El estudiante" : nombreCompleto(bitacora.getEstudiante());
        String mensaje = "La bitácora del " + bitacora.getFecha() + " de " + nombreEstudiante
                + " presenta discrepancias: " + String.join(" ", discrepancias);
        String ruta = bitacora.getVinculacion() != null ? "/dashboard/vinculacion" : "/dashboard/practicas";

        Usuario tutor = bitacora.getPractica() != null ? bitacora.getPractica().getTutor()
                : bitacora.getVinculacion() != null ? bitacora.getVinculacion().getTutor() : null;
        if (tutor != null) {
            notificacionEmitter.emitir(tutor, "bitacora_discrepancia", "Discrepancia en bitácora", mensaje,
                    "bitacora", bitacora.getId().longValue(), ruta);
        }

        if (bitacora.getEstudiante() != null && bitacora.getEstudiante().getCarrera() != null) {
            List<CoordinadorCarrera> coordinadores =
                    coordinadorCarreraRepository.findByCarreraIgnoreCase(bitacora.getEstudiante().getCarrera());
            coordinadores.stream()
                    .map(CoordinadorCarrera::getUsuario)
                    .filter(coordinadorUsuario -> coordinadorUsuario != null
                            && (tutor == null || !coordinadorUsuario.getId().equals(tutor.getId())))
                    .forEach(coordinadorUsuario -> notificacionEmitter.emitir(
                            coordinadorUsuario, "bitacora_discrepancia", "Discrepancia en bitácora", mensaje,
                            "bitacora", bitacora.getId().longValue(), ruta));
        }
    }

    private List<String> detectarDiscrepancias(Bitacora bitacora, Asistencia asistenciaDelDia) {
        List<String> razones = new ArrayList<>();
        if (asistenciaDelDia == null) {
            razones.add("No tiene respaldo de asistencia ese día.");
        }
        if (bitacora.getFechaRegistro() != null && bitacora.getFecha() != null
                && !bitacora.getFechaRegistro().equals(bitacora.getFecha())) {
            razones.add("Fue registrada el " + bitacora.getFechaRegistro()
                    + ", fuera de la fecha reportada (" + bitacora.getFecha() + ").");
        }
        if (bitacora.getHorasExtra() != null && bitacora.getHorasExtra() > 0) {
            razones.add("Reporta " + bitacora.getHorasExtra() + " horas extra que requieren verificación.");
        }
        return razones;
    }

    private Asistencia buscarAsistenciaDelDia(Bitacora bitacora) {
        if (bitacora.getPractica() != null && bitacora.getPractica().getId() != null) {
            return asistenciaRepository.findByPracticaIdAndFecha(bitacora.getPractica().getId(), bitacora.getFecha())
                    .orElse(null);
        }
        if (bitacora.getVinculacion() != null && bitacora.getVinculacion().getId() != null) {
            return asistenciaRepository.findByVinculacionIdAndFecha(bitacora.getVinculacion().getId(), bitacora.getFecha())
                    .orElse(null);
        }
        return null;
    }

    private String nombreCompleto(Estudiante estudiante) {
        Usuario usuarioEstudiante = estudiante.getUsuario();
        if (usuarioEstudiante == null) return "el estudiante";
        String nombres = usuarioEstudiante.getNombre() == null ? "" : usuarioEstudiante.getNombre();
        String apellidos = usuarioEstudiante.getApellido() == null ? "" : usuarioEstudiante.getApellido();
        String nombre = (nombres + " " + apellidos).trim();
        return nombre.isEmpty() ? "el estudiante" : nombre;
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

    private List<Bitacora> bitacorasDelTutor(Integer tutorId) {
        Map<Integer, Bitacora> asignadas = new LinkedHashMap<>();
        bitacoraRepository.findByPracticaTutorId(tutorId)
                .forEach(bitacora -> asignadas.put(bitacora.getId(), bitacora));
        bitacoraRepository.findByVinculacionTutorId(tutorId)
                .forEach(bitacora -> asignadas.put(bitacora.getId(), bitacora));
        return asignadas.values().stream().toList();
    }

    private boolean tutorAsignadoBitacora(Bitacora bitacora, Integer tutorId) {
        if (bitacora.getPractica() != null && bitacora.getPractica().getTutor() != null) {
            return tutorId.equals(bitacora.getPractica().getTutor().getId());
        }
        if (bitacora.getVinculacion() != null && bitacora.getVinculacion().getTutor() != null) {
            return tutorId.equals(bitacora.getVinculacion().getTutor().getId());
        }
        return false;
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a las bitácoras de este estudiante.");
    }

    private void verificarPuedeGestionarEstudiante(Integer estudianteId, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado."));
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && tutorAsignado(estudianteId, usuario.getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar bitácoras de este estudiante.");
    }

    private void verificarPuedeRevisarBitacora(Bitacora bitacora, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR")
                && carreraVisible(authentication, bitacora.getEstudiante())
                && procesoVisible(authentication, bitacora)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && tutorAsignadoBitacora(bitacora, usuario.getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes revisar esta bitácora.");
    }

    private boolean procesoVisible(Authentication authentication, Bitacora bitacora) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        if (bitacora.getPractica() != null) {
            return alcanceCoordinador.procesoVisible(authentication, PRACTICAS);
        }
        if (bitacora.getVinculacion() != null) {
            return alcanceCoordinador.procesoVisible(authentication, VINCULACION);
        }
        return false;
    }

    private void verificarProceso(Authentication authentication, String proceso) {
        if (!alcanceCoordinador.procesoVisible(authentication, proceso)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no puede gestionar bitácoras de este proceso.");
        }
    }
}
