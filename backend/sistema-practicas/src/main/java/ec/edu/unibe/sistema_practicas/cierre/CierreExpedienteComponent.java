package ec.edu.unibe.sistema_practicas.cierre;

import ec.edu.unibe.sistema_practicas.auditoria.Auditoria;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaRepository;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequerido;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequeridoRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CierreExpedienteComponent {

    private static final String PROCESO_PRACTICAS = "PRACTICAS";
    private static final String PROCESO_VINCULACION = "VINCULACION";

    private final DocumentoRequeridoRepository requeridoRepository;
    private final DocEstudianteRepository documentoRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    public void validarDocumentosCierre(Estudiante estudiante, String proceso, String etapa) {
        if (estudiante == null || estudiante.getId() == null) {
            throw new IllegalArgumentException("No se puede cerrar: expediente sin estudiante asociado.");
        }
        List<DocumentoRequerido> requeridos = requeridoRepository.findByActivoTrue().stream()
                .filter(req -> Boolean.TRUE.equals(req.getObligatorio()))
                .filter(req -> "CIERRE".equalsIgnoreCase(valor(req.getMomento())))
                .filter(req -> aplicaProceso(req, proceso))
                .filter(req -> aplicaCarrera(req, estudiante.getCarrera()))
                .filter(req -> aplicaEtapa(req, etapa))
                .toList();

        for (DocumentoRequerido requerido : requeridos) {
            String procesoDocumento = procesoDocumento(requerido, proceso);
            DocEstudiante doc = documentoRepository
                    .findByEstudianteIdAndTipoDocumentoAndProceso(
                            estudiante.getId(), requerido.getTipoDocumento(), procesoDocumento)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se puede cerrar: falta documento de cierre '" + requerido.getNombre() + "'."));
            if (doc.getUrlArchivo() == null || doc.getUrlArchivo().isBlank()
                    || !"aprobado".equalsIgnoreCase(valor(doc.getEstado()))) {
                throw new IllegalArgumentException(
                        "No se puede cerrar: el documento de cierre '" + requerido.getNombre()
                                + "' debe estar cargado y aprobado.");
            }
        }
    }

    public void registrarCierrePractica(Practica practica, Usuario usuario, Map<String, Object> snapshot) {
        String json = objectMapper.writeValueAsString(snapshot);
        practica.setCerradoPor(usuario);
        practica.setCerradoEn(LocalDateTime.now());
        practica.setCierreSnapshot(json);
        registrarAuditoria("PRACTICAS", "CIERRE_FORMAL", usuario, null, snapshot);
    }

    public void registrarCierreVinculacion(Vinculacion vinculacion, Usuario usuario, Map<String, Object> snapshot) {
        String json = objectMapper.writeValueAsString(snapshot);
        vinculacion.setCerradoPor(usuario);
        vinculacion.setCerradoEn(LocalDateTime.now());
        vinculacion.setCierreSnapshot(json);
        registrarAuditoria("VINCULACION", "CIERRE_FORMAL", usuario, null, snapshot);
    }

    public void validarModificacionPermitida(Practica practica, Authentication authentication,
                                             Usuario usuario, String justificacion,
                                             String tabla, Object recursoId) {
        if (!estaCerrada(practica)) return;
        validarAdminConJustificacion(authentication, justificacion);
        registrarEdicionCerrado(tabla, usuario, justificacion, "PRACTICAS", practica.getId(), recursoId);
    }

    public void validarModificacionPermitida(Vinculacion vinculacion, Authentication authentication,
                                             Usuario usuario, String justificacion,
                                             String tabla, Object recursoId) {
        if (!estaCerrada(vinculacion)) return;
        validarAdminConJustificacion(authentication, justificacion);
        registrarEdicionCerrado(tabla, usuario, justificacion, "VINCULACION", vinculacion.getId(), recursoId);
    }

    public void validarDocumentoEditable(Estudiante estudiante, String proceso,
                                         Authentication authentication, Usuario usuario,
                                         String justificacion, Object documentoId) {
        String procesoFinal = valor(proceso).toUpperCase(Locale.ROOT);
        if (!PROCESO_PRACTICAS.equals(procesoFinal) && !PROCESO_VINCULACION.equals(procesoFinal)) return;
        if (tieneExpedienteActivo(estudiante, procesoFinal) || !tieneExpedienteCerrado(estudiante, procesoFinal)) return;

        validarAdminConJustificacion(authentication, justificacion);
        registrarEdicionCerrado("DOCS_ESTUDIANTE", usuario, justificacion, procesoFinal, null, documentoId);
    }

    public Map<String, Object> actaPractica(Practica practica) {
        if (practica == null || !"completado".equalsIgnoreCase(valor(practica.getEstado()))) {
            throw new IllegalArgumentException("El acta solo está disponible para prácticas completadas formalmente.");
        }
        Map<String, Object> acta = baseActa("PRACTICAS", practica.getId(), practica.getEstado(),
                practica.getFechaInicio(), practica.getFechaFin(), practica.getPeriodoAcademico(),
                practica.getHorasRequeridas(), practica.getHorasCompletadas(),
                practica.getCerradoPor(), practica.getCerradoEn(), practica.getCierreSnapshot());
        Estudiante estudiante = practica.getEstudiante();
        acta.put("estudiante", estudianteResumen(estudiante));
        acta.put("entidad", practica.getEmpresa() == null ? null : practica.getEmpresa().getNombre());
        acta.put("tutor", usuarioNombre(practica.getTutor()));
        return acta;
    }

    public Map<String, Object> actaVinculacion(Vinculacion vinculacion) {
        if (vinculacion == null || !"completado".equalsIgnoreCase(valor(vinculacion.getEstado()))) {
            throw new IllegalArgumentException("El acta solo está disponible para vinculaciones completadas formalmente.");
        }
        Map<String, Object> acta = baseActa("VINCULACION", vinculacion.getId(), vinculacion.getEstado(),
                vinculacion.getFechaInicio(), vinculacion.getFechaFin(), vinculacion.getPeriodoAcademico(),
                vinculacion.getHorasRequeridas(), vinculacion.getHorasCompletadas(),
                vinculacion.getCerradoPor(), vinculacion.getCerradoEn(), vinculacion.getCierreSnapshot());
        Estudiante estudiante = vinculacion.getEstudiante();
        acta.put("estudiante", estudianteResumen(estudiante));
        acta.put("entidad", vinculacion.getFundacion() == null ? null : vinculacion.getFundacion().getNombre());
        acta.put("proyecto", vinculacion.getProyecto() == null ? null : vinculacion.getProyecto().getNombre());
        acta.put("tutor", usuarioNombre(vinculacion.getTutor()));
        return acta;
    }

    public boolean estaCerrada(Practica practica) {
        return practica != null
                && ("completado".equalsIgnoreCase(valor(practica.getEstado()))
                || "reprobado".equalsIgnoreCase(valor(practica.getEstado()))
                || "retirado".equalsIgnoreCase(valor(practica.getEstado()))
                || practica.getCerradoEn() != null);
    }

    public boolean estaCerrada(Vinculacion vinculacion) {
        return vinculacion != null
                && ("completado".equalsIgnoreCase(valor(vinculacion.getEstado()))
                || "reprobado".equalsIgnoreCase(valor(vinculacion.getEstado()))
                || "retirado".equalsIgnoreCase(valor(vinculacion.getEstado()))
                || vinculacion.getCerradoEn() != null);
    }

    private boolean tieneExpedienteActivo(Estudiante estudiante, String proceso) {
        if (estudiante == null || estudiante.getId() == null) return false;
        if (PROCESO_PRACTICAS.equals(proceso)) {
            return practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                    .anyMatch(p -> "pendiente".equalsIgnoreCase(valor(p.getEstado()))
                            || "en_curso".equalsIgnoreCase(valor(p.getEstado())));
        }
        return vinculacionRepository.findByEstudianteId(estudiante.getId()).stream()
                .anyMatch(v -> "pendiente".equalsIgnoreCase(valor(v.getEstado()))
                        || "en_curso".equalsIgnoreCase(valor(v.getEstado())));
    }

    private boolean tieneExpedienteCerrado(Estudiante estudiante, String proceso) {
        if (estudiante == null || estudiante.getId() == null) return false;
        if (PROCESO_PRACTICAS.equals(proceso)) {
            return practicaRepository.findByEstudianteId(estudiante.getId()).stream().anyMatch(this::estaCerrada);
        }
        return vinculacionRepository.findByEstudianteId(estudiante.getId()).stream().anyMatch(this::estaCerrada);
    }

    private void validarAdminConJustificacion(Authentication authentication, String justificacion) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El expediente está cerrado y no admite modificaciones.");
        }
        if (justificacion == null || justificacion.isBlank()) {
            throw new IllegalArgumentException(
                    "El expediente está cerrado. Administración debe enviar X-Justificacion-Admin para modificarlo.");
        }
    }

    private void registrarEdicionCerrado(String tabla, Usuario usuario, String justificacion,
                                         String proceso, Object expedienteId, Object recursoId) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("proceso", proceso);
        datos.put("expedienteId", expedienteId);
        datos.put("recursoId", recursoId);
        datos.put("justificacion", justificacion == null ? "" : justificacion.trim());
        datos.put("registradoEn", LocalDateTime.now().toString());
        registrarAuditoria(tabla, "EDICION_CERRADO", usuario, null, datos);
    }

    private void registrarAuditoria(String tabla, String accion, Usuario usuario,
                                    Object antes, Object despues) {
        Auditoria auditoria = new Auditoria();
        auditoria.setTablaAfectada(tabla);
        auditoria.setAccion(accion);
        auditoria.setDatosAntes(antes == null ? null : objectMapper.writeValueAsString(antes));
        auditoria.setDatosDespues(despues == null ? null : objectMapper.writeValueAsString(despues));
        auditoria.setUsuario(usuario);
        auditoria.setFecha(LocalDateTime.now());
        auditoriaRepository.save(auditoria);
    }

    private boolean aplicaProceso(DocumentoRequerido requerido, String proceso) {
        String reqProceso = valor(requerido.getProceso()).toUpperCase(Locale.ROOT);
        String procesoFinal = valor(proceso).toUpperCase(Locale.ROOT);
        return "GENERAL".equals(reqProceso) || procesoFinal.equals(reqProceso);
    }

    private boolean aplicaCarrera(DocumentoRequerido requerido, String carrera) {
        String reqCarrera = valor(requerido.getCarrera());
        return reqCarrera.isBlank() || reqCarrera.equals(carrera);
    }

    private boolean aplicaEtapa(DocumentoRequerido requerido, String etapa) {
        String reqEtapa = valor(requerido.getEtapa());
        return reqEtapa.isBlank() || reqEtapa.equals(etapa);
    }

    private String procesoDocumento(DocumentoRequerido requerido, String procesoDefault) {
        String proceso = valor(requerido.getProceso()).toUpperCase(Locale.ROOT);
        return proceso.isBlank() ? procesoDefault : proceso;
    }

    private Map<String, Object> baseActa(String proceso, Integer id, String estado,
                                         LocalDate fechaInicio, LocalDate fechaFin, String periodoAcademico,
                                         Integer horasRequeridas, Integer horasCompletadas,
                                         Usuario cerradoPor, LocalDateTime cerradoEn, String snapshotJson) {
        Map<String, Object> acta = new LinkedHashMap<>();
        acta.put("proceso", proceso);
        acta.put("expedienteId", id);
        acta.put("estado", estado);
        acta.put("fechaInicio", fechaInicio);
        acta.put("fechaFin", fechaFin);
        acta.put("periodoAcademico", periodoAcademico);
        acta.put("horasRequeridas", horasRequeridas);
        acta.put("horasCompletadas", horasCompletadas);
        acta.put("cerradoPor", usuarioNombre(cerradoPor));
        acta.put("cerradoEn", cerradoEn);
        acta.put("snapshotJson", snapshotJson);
        return acta;
    }

    private Map<String, Object> estudianteResumen(Estudiante estudiante) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        if (estudiante == null) return resumen;
        resumen.put("id", estudiante.getId());
        resumen.put("nombre", estudiante.getUsuario() == null ? null : usuarioNombre(estudiante.getUsuario()));
        resumen.put("matricula", estudiante.getMatricula());
        resumen.put("carrera", estudiante.getCarrera());
        resumen.put("semestre", estudiante.getSemestre());
        return resumen;
    }

    private String usuarioNombre(Usuario usuario) {
        if (usuario == null) return null;
        return (valor(usuario.getNombre()) + " " + valor(usuario.getApellido())).trim();
    }

    private String valor(String value) {
        return value == null ? "" : value.trim();
    }
}
