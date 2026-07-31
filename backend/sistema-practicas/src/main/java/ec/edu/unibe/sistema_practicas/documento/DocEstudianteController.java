package ec.edu.unibe.sistema_practicas.documento;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EtapaAcademicaComponent;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

@RestController
@RequestMapping("/api/documentos")
@RequiredArgsConstructor
@Slf4j
public class DocEstudianteController {

    private static final Set<String> TIPOS_VALIDOS = Set.of(
            "cv", "carta", "cedula", "carta_aceptacion", "informe_final", "certificado");
    private static final Set<String> PROCESOS_VALIDOS = Set.of("GENERAL", "PRACTICAS", "VINCULACION");
    // Tipos del catalogo bajo proceso=PRACTICAS que distinguen Practica I de
    // Practica II (no heredan automaticamente de una practica a la siguiente).
    private static final Set<String> TIPOS_CON_ETAPA_PRACTICAS = Set.of("carta", "carta_aceptacion");
    private static final Set<String> CONTENT_TYPES_VALIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final Set<String> ESTADOS_REVISION_VALIDOS = Set.of("aprobado", "requiere_correccion");
    private static final Set<String> ESTADOS_LISTADO_VALIDOS = Set.of(
            "pendiente", "cargado", "en_revision", "aprobado", "rechazado", "requiere_correccion");

    private final DocEstudianteRepository documentoRepository;
    private final DocumentoRequeridoRepository requeridoRepository;
    private final EstudianteRepository estudianteRepository;
    private final SupabaseStorageComponent storageComponent;
    private final AlcanceCoordinador alcanceCoordinador;
    private final NotificacionEmitter notificacionEmitter;
    private final CierreExpedienteComponent cierreExpedienteComponent;
    private final AuditoriaEmitter auditoriaEmitter;
    private final EtapaAcademicaComponent etapaAcademicaComponent;

    @Value("${app.documentos.max-file-size-bytes:${APP_DOCUMENTOS_MAX_FILE_SIZE_BYTES:5242880}}")
    private long maxFileSizeBytes;

    private Optional<Estudiante> estudianteActual(Usuario usuario) {
        if (usuario == null) return Optional.empty();
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail());
    }

    private List<DocEstudiante> documentosEstudiante(Integer estudianteId, String proceso) {
        Optional<String> procesoFiltro = procesoFiltro(proceso);
        return procesoFiltro
                .map(p -> documentoRepository.findByEstudianteIdAndProceso(estudianteId, p))
                .orElseGet(() -> documentoRepository.findByEstudianteId(estudianteId));
    }

    private List<String> tiposCargados(Integer estudianteId, String proceso) {
        return documentosEstudiante(estudianteId, proceso).stream()
                .map(DocEstudiante::getTipoDocumento)
                .distinct()
                .toList();
    }

    @GetMapping("/me")
    public List<String> getMisDocumentos(@RequestParam(required = false) String proceso,
                                         @AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> tiposCargados(est.getId(), proceso))
                .orElse(List.of());
    }

    @GetMapping("/me/detalle")
    public List<DocEstudiante> getMisDocumentosDetalle(@RequestParam(required = false) String proceso,
                                                       @AuthenticationPrincipal Usuario usuario) {
        return estudianteActual(usuario)
                .map(est -> documentosEstudiante(est.getId(), proceso))
                .orElse(List.of());
    }

    @GetMapping("/requeridos")
    public List<DocumentoRequerido> getRequeridos(@RequestParam(required = false) String proceso,
                                                  Authentication authentication) {
        List<DocumentoRequerido> requeridos = proceso == null || proceso.isBlank()
                ? requeridoRepository.findByActivoTrue()
                : requeridoRepository.findByProcesoAndActivoTrue(procesoValidado(proceso));
        return requeridos.stream()
                .filter(requerido -> procesoVisibleParaConsulta(authentication, requerido.getProceso()))
                .toList();
    }

    @GetMapping("/revision")
    public List<DocEstudiante> getDocumentosRevision(@RequestParam(required = false) String proceso,
                                                     @RequestParam(required = false) String estado,
                                                     @RequestParam(required = false) String carrera,
                                                     @RequestParam(required = false) Integer estudianteId,
                                                     Authentication authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes revisar documentos.");
        }
        Optional<String> procesoFiltro = procesoFiltro(proceso);
        String estadoFiltro = normalizarOpcional(estado).map(this::estadoListado).orElse(null);
        String carreraFiltro = normalizarOpcional(carrera).orElse(null);

        // Perf: cuando el llamador ya conoce el estudiante (p.ej. abrir su
        // expediente), acota en la query en vez de traer TODA la tabla de
        // documentos en revision y filtrar en memoria.
        List<DocEstudiante> base = estudianteId != null
                ? documentoRepository.findByEstudianteId(estudianteId)
                : documentoRepository.findAll();

        return base.stream()
                .filter(doc -> doc.getUrlArchivo() != null && !doc.getUrlArchivo().isBlank())
                .filter(doc -> procesoFiltro.map(p -> p.equals(doc.getProceso())).orElse(true))
                .filter(doc -> estadoFiltro == null || estadoFiltro.equals(doc.getEstado()))
                .filter(doc -> carreraFiltro == null || carreraFiltro.equals(carreraDocumento(doc)))
                .filter(doc -> hasRole(authentication, "ADMIN") || dentroDelAlcance(doc, authentication))
                .sorted(Comparator.comparing(DocEstudiante::getFechaSubida,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // Registra el requisito documental en el expediente. El archivo real se
    // carga en /me/{id}/archivo; las postulaciones solo aceptan documentos con
    // ruta de archivo almacenada.
    @PostMapping("/me")
    public List<String> subir(@RequestParam String tipo,
                              @RequestParam(defaultValue = "GENERAL") String proceso,
                              @AuthenticationPrincipal Usuario usuario,
                              Authentication authentication) {
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo de documento invalido.");
        }
        String procesoFinal = procesoValidado(proceso);
        Estudiante estudiante = estudianteActual(usuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tu usuario no tiene un expediente de estudiante asociado."));
        etapaAcademicaComponent.exigirProcesoDocumentalActual(estudiante, procesoFinal);
        cierreExpedienteComponent.validarDocumentoEditable(
                estudiante, procesoFinal, authentication, usuario, null, null);

        DocEstudiante doc = documentoRepository
                .findByEstudianteIdAndTipoDocumentoAndProceso(estudiante.getId(), tipo, procesoFinal)
                .orElseGet(() -> {
                    DocEstudiante nuevo = new DocEstudiante();
                    nuevo.setEstudiante(estudiante);
                    nuevo.setTipoDocumento(tipo);
                    nuevo.setProceso(procesoFinal);
                    return nuevo;
                });
        doc.setFechaSubida(LocalDateTime.now());
        doc.setProceso(procesoFinal);
        doc.setCarrera(estudiante.getCarrera());
        if (EtapaAcademicaComponent.PROCESO_PRACTICAS.equals(procesoFinal) && TIPOS_CON_ETAPA_PRACTICAS.contains(tipo)) {
            doc.setEtapa(etapaAcademicaComponent.etapaPracticaActual(estudiante));
        }
        doc.setEstado(doc.getUrlArchivo() == null || doc.getUrlArchivo().isBlank() ? "pendiente" : "cargado");
        documentoRepository.save(doc);

        return tiposCargados(estudiante.getId(), procesoFinal);
    }

    @PostMapping(value = "/me/{id}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocEstudiante subirArchivo(@PathVariable Integer id,
                                      @RequestParam("archivo") MultipartFile archivo,
                                      @AuthenticationPrincipal Usuario usuario,
                                      Authentication authentication) {
        Estudiante estudiante = estudianteActual(usuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tu usuario no tiene un expediente de estudiante asociado."));
        DocEstudiante doc = documentoPropio(id, estudiante);
        etapaAcademicaComponent.exigirProcesoDocumentalActual(estudiante, doc.getProceso());
        cierreExpedienteComponent.validarDocumentoEditable(
                estudiante, doc.getProceso(), authentication, usuario, null, doc.getId());
        validarArchivo(archivo);

        String ruta = rutaStorage(estudiante, doc, archivo);
        try {
            storageComponent.subir(ruta, archivo);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        } catch (IOException ex) {
            log.warn("No se pudo conectar con Supabase Storage al subir documento. causa={}",
                    ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con Storage de documentos.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La carga del archivo fue interrumpida.");
        }

        doc.setUrlArchivo(ruta);
        doc.setFechaSubida(LocalDateTime.now());
        doc.setEstado("cargado");
        doc.setObservacion(null);
        doc.setRevisadoPor(null);
        doc.setFechaRevision(null);
        return documentoRepository.save(doc);
    }

    @GetMapping("/me/{id}/archivo")
    public ArchivoDocumentoResponse getMiArchivo(@PathVariable Integer id,
                                                 @AuthenticationPrincipal Usuario usuario) {
        Estudiante estudiante = estudianteActual(usuario)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tu usuario no tiene un expediente de estudiante asociado."));
        return urlFirmada(documentoPropio(id, estudiante));
    }

    @GetMapping("/{id}/archivo")
    public ArchivoDocumentoResponse getArchivoRevision(@PathVariable Integer id,
                                                       Authentication authentication) {
        DocEstudiante doc = documentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado."));

        if (hasRole(authentication, "ADMIN")) {
            return urlFirmada(doc);
        }
        if (hasRole(authentication, "COORDINADOR") && dentroDelAlcance(doc, authentication)) {
            return urlFirmada(doc);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes consultar este archivo.");
    }

    @PutMapping("/{id}/revision")
    public DocEstudiante revisarDocumento(@PathVariable Integer id,
                                          @RequestBody RevisionDocumentoRequest request,
                                          Authentication authentication,
                                          @AuthenticationPrincipal Usuario usuario,
                                          @RequestHeader(value = "X-Justificacion-Admin", required = false) String justificacionAdmin) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes revisar documentos.");
        }
        DocEstudiante doc = documentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado."));
        if (hasRole(authentication, "COORDINADOR") && !dentroDelAlcance(doc, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes revisar documentos de otra carrera.");
        }
        if (doc.getUrlArchivo() == null || doc.getUrlArchivo().isBlank()) {
            throw new IllegalArgumentException("El documento aun no tiene archivo cargado.");
        }
        cierreExpedienteComponent.validarDocumentoEditable(
                doc.getEstudiante(), doc.getProceso(), authentication, usuario, justificacionAdmin, doc.getId());

        String estadoFinal = estadoRevision(request.estado());
        String observacion = request.observacion() == null ? "" : request.observacion().trim();
        if ("requiere_correccion".equals(estadoFinal) && observacion.isBlank()) {
            throw new IllegalArgumentException("La observacion es obligatoria cuando se solicita correccion.");
        }

        doc.setEstado(estadoFinal);
        doc.setObservacion(observacion.isBlank() ? null : observacion);
        doc.setRevisadoPor(usuario);
        doc.setFechaRevision(LocalDateTime.now());
        DocEstudiante guardado = documentoRepository.save(doc);
        emitirNotificacionRevision(guardado);
        return guardado;
    }

    @PostMapping("/requeridos")
    @Transactional
    public DocumentoRequerido createRequerido(@RequestBody DocumentoRequerido requerido,
                                              @AuthenticationPrincipal Usuario usuario,
                                              Authentication authentication) {
        validarRequerido(requerido);
        verificarProcesoGestionable(authentication, requerido.getProceso());
        requerido.setSolicitadoPor(usuario);
        requerido.setCreatedAt(LocalDateTime.now());
        requerido.setUpdatedAt(LocalDateTime.now());
        if (requerido.getActivo() == null) requerido.setActivo(true);
        DocumentoRequerido guardado = requeridoRepository.save(requerido);
        auditoriaEmitter.registrar("DOCUMENTOS_REQUERIDOS", "CREAR_REQUISITO", usuario,
                null, snapshotRequerido(guardado));
        return guardado;
    }

    @PutMapping("/requeridos/{id}")
    @Transactional
    public ResponseEntity<DocumentoRequerido> updateRequerido(@PathVariable Integer id,
                                                              @RequestBody DocumentoRequerido details,
                                                              @AuthenticationPrincipal Usuario usuario,
                                                              Authentication authentication) {
        return requeridoRepository.findById(id)
                .map(requerido -> {
                    verificarProcesoGestionable(authentication, requerido.getProceso());
                    Map<String, Object> antes = snapshotRequerido(requerido);
                    validarRequerido(details);
                    verificarProcesoGestionable(authentication, details.getProceso());
                    requerido.setProceso(details.getProceso());
                    requerido.setCarrera(details.getCarrera());
                    requerido.setEtapa(details.getEtapa());
                    requerido.setTipoDocumento(details.getTipoDocumento());
                    requerido.setNombre(details.getNombre());
                    requerido.setDescripcion(details.getDescripcion());
                    requerido.setObligatorio(details.getObligatorio());
                    requerido.setMomento(details.getMomento());
                    requerido.setActivo(details.getActivo());
                    requerido.setSolicitadoPor(usuario);
                    requerido.setUpdatedAt(LocalDateTime.now());
                    DocumentoRequerido guardado = requeridoRepository.save(requerido);
                    auditoriaEmitter.registrar("DOCUMENTOS_REQUERIDOS", "ACTUALIZAR_REQUISITO", usuario,
                            antes, snapshotRequerido(guardado));
                    return ResponseEntity.ok(guardado);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/requeridos/{id}")
    @Transactional
    public ResponseEntity<Void> deleteRequerido(@PathVariable Integer id,
                                                 @AuthenticationPrincipal Usuario usuario,
                                                 Authentication authentication) {
        return requeridoRepository.findById(id)
                .map(requerido -> {
                    verificarProcesoGestionable(authentication, requerido.getProceso());
                    Map<String, Object> antes = snapshotRequerido(requerido);
                    requerido.setActivo(false);
                    requerido.setUpdatedAt(LocalDateTime.now());
                    DocumentoRequerido guardado = requeridoRepository.save(requerido);
                    auditoriaEmitter.registrar("DOCUMENTOS_REQUERIDOS", "DESACTIVAR_REQUISITO", usuario,
                            antes, snapshotRequerido(guardado));
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> snapshotRequerido(DocumentoRequerido requerido) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", requerido.getId());
        datos.put("proceso", requerido.getProceso());
        datos.put("carrera", requerido.getCarrera());
        datos.put("etapa", requerido.getEtapa());
        datos.put("tipoDocumento", requerido.getTipoDocumento());
        datos.put("nombre", requerido.getNombre());
        datos.put("obligatorio", requerido.getObligatorio());
        datos.put("momento", requerido.getMomento());
        datos.put("activo", requerido.getActivo());
        return datos;
    }

    private DocEstudiante documentoPropio(Integer id, Estudiante estudiante) {
        DocEstudiante doc = documentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado."));
        if (doc.getEstudiante() == null
                || doc.getEstudiante().getId() == null
                || !doc.getEstudiante().getId().equals(estudiante.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar este documento.");
        }
        return doc;
    }

    private ArchivoDocumentoResponse urlFirmada(DocEstudiante doc) {
        if (doc.getUrlArchivo() == null || doc.getUrlArchivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El documento aun no tiene archivo cargado.");
        }
        try {
            return new ArchivoDocumentoResponse(storageComponent.crearUrlFirmada(doc.getUrlArchivo()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        } catch (IOException ex) {
            log.warn("No se pudo conectar con Supabase Storage al generar URL firmada. causa={}",
                    ex.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo conectar con Storage de documentos.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La generacion del enlace fue interrumpida.");
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar un archivo.");
        }
        if (archivo.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("El archivo supera el tamano maximo permitido.");
        }
        String contentType = contentType(archivo);
        if (!CONTENT_TYPES_VALIDOS.contains(contentType)) {
            throw new IllegalArgumentException("Solo se permiten archivos PDF o imagenes PNG/JPG/WEBP.");
        }
    }

    private String rutaStorage(Estudiante estudiante, DocEstudiante doc, MultipartFile archivo) {
        String proceso = doc.getProceso() == null ? "GENERAL" : doc.getProceso();
        String tipo = doc.getTipoDocumento().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return "estudiantes/%d/%s/%s/%d-%s.%s".formatted(
                estudiante.getId(),
                proceso.toLowerCase(Locale.ROOT),
                tipo,
                doc.getId(),
                UUID.randomUUID(),
                extension(archivo));
    }

    private String extension(MultipartFile archivo) {
        return switch (contentType(archivo)) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Tipo de archivo invalido.");
        };
    }

    private String contentType(MultipartFile archivo) {
        return Optional.ofNullable(archivo.getContentType())
                .orElse("")
                .toLowerCase(Locale.ROOT);
    }

    private boolean dentroDelAlcance(DocEstudiante doc, Authentication authentication) {
        return procesoVisibleParaConsulta(authentication, doc.getProceso())
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> {
                    String carrera = carreraDocumento(doc);
                    return carrera != null && carreras.contains(carrera);
                })
                .orElse(false);
    }

    private boolean procesoVisibleParaConsulta(Authentication authentication, String proceso) {
        return "GENERAL".equalsIgnoreCase(proceso)
                || alcanceCoordinador.procesoVisible(authentication, proceso);
    }

    private void verificarProcesoGestionable(Authentication authentication, String proceso) {
        if (!hasRole(authentication, "COORDINADOR")) return;
        if ("GENERAL".equalsIgnoreCase(proceso)
                || !alcanceCoordinador.procesoVisible(authentication, proceso)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no puede gestionar requisitos de este proceso.");
        }
    }

    private String carreraDocumento(DocEstudiante doc) {
        return doc.getCarrera() != null
                ? doc.getCarrera()
                : doc.getEstudiante() == null ? null : doc.getEstudiante().getCarrera();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private Optional<String> procesoFiltro(String proceso) {
        if (proceso == null || proceso.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(procesoValidado(proceso));
    }

    private String procesoValidado(String proceso) {
        String normalizado = proceso == null ? "" : proceso.trim().toUpperCase(Locale.ROOT);
        if (!PROCESOS_VALIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("Proceso documental invalido.");
        }
        return normalizado;
    }

    private Optional<String> normalizarOpcional(String value) {
        if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private String estadoListado(String estado) {
        String normalizado = estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT);
        if (!ESTADOS_LISTADO_VALIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado documental invalido.");
        }
        return normalizado;
    }

    private String estadoRevision(String estado) {
        String normalizado = estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT);
        if (!ESTADOS_REVISION_VALIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado de revision documental invalido.");
        }
        return normalizado;
    }

    private void emitirNotificacionRevision(DocEstudiante doc) {
        Usuario destino = doc.getEstudiante() == null ? null : doc.getEstudiante().getUsuario();
        String link = "VINCULACION".equals(doc.getProceso()) ? "/dashboard/vinculacion" : "/dashboard/practicas";
        if ("aprobado".equals(doc.getEstado())) {
            notificacionEmitter.emitir(destino, "documento_aprobado",
                    "Documento aprobado",
                    "Tu documento %s fue aprobado por gestion academica.".formatted(doc.getTipoDocumento()),
                    "documento_estudiante", doc.getId().longValue(), link);
        } else if ("requiere_correccion".equals(doc.getEstado())) {
            notificacionEmitter.emitir(destino, "documento_rechazado",
                    "Documento requiere correccion",
                    "Tu documento %s requiere correccion. Revisa la observacion y vuelve a cargarlo."
                            .formatted(doc.getTipoDocumento()),
                    "documento_estudiante", doc.getId().longValue(), link);
        }
    }

    private void validarRequerido(DocumentoRequerido requerido) {
        requerido.setProceso(procesoValidado(requerido.getProceso()));
        if (requerido.getTipoDocumento() == null || requerido.getTipoDocumento().isBlank()) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio.");
        }
        if (requerido.getNombre() == null || requerido.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del documento requerido es obligatorio.");
        }
        if (requerido.getMomento() == null || requerido.getMomento().isBlank()) {
            requerido.setMomento("PRE_POSTULACION");
        }
    }

    public record ArchivoDocumentoResponse(String url) {}
    public record RevisionDocumentoRequest(String estado, String observacion) {}
}
