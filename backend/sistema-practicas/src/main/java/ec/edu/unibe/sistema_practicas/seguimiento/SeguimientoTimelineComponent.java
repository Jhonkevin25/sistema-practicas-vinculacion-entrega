package ec.edu.unibe.sistema_practicas.seguimiento;

import ec.edu.unibe.sistema_practicas.auditoria.Auditoria;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaRepository;
import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccion;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccionRepository;
import ec.edu.unibe.sistema_practicas.notacoordinacion.NotaCoordinacion;
import ec.edu.unibe.sistema_practicas.notacoordinacion.NotaCoordinacionRepository;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SeguimientoTimelineComponent {

    private final DocEstudianteRepository documentoRepository;
    private final BitacoraRepository bitacoraRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final EncuestaSatisfaccionRepository encuestaRepository;
    private final NotaCoordinacionRepository notaCoordinacionRepository;

    public LineaTiempoExpedienteResponse paraPractica(Practica practica) {
        List<EventoLineaTiempo> eventos = new ArrayList<>();
        agregarInicio(eventos, "PRACTICAS", practica.getId(), practica.getFechaInicio(), practica.getEstado());
        agregarDocumentos(eventos, practica.getEstudiante() == null ? null : practica.getEstudiante().getId(), "PRACTICAS");
        agregarBitacoras(eventos, bitacoraRepository.findByPracticaId(practica.getId()));
        agregarAsistencias(eventos, asistenciaRepository.findByPracticaId(practica.getId()));
        agregarNotasCoordinacion(eventos, notaCoordinacionRepository.findByPracticaId(practica.getId()));
        agregarAuditoriaNotas(eventos, practica.getId(), null);
        agregarEncuestas(eventos, encuestaRepository.findByPracticaId(practica.getId()));
        agregarFinalizacion(eventos, practica.getEstado(), practica.getMotivoFinalizacion(),
                practica.getCerradoEn(), practica.getCerradoPor(), "PRACTICAS");
        return respuesta("PRACTICAS", practica.getId(), practica.getPeriodoAcademico(), practica.getEstado(),
                practica.getMotivoFinalizacion(), practica.getCerradoEn(), eventos);
    }

    public LineaTiempoExpedienteResponse paraVinculacion(Vinculacion vinculacion) {
        List<EventoLineaTiempo> eventos = new ArrayList<>();
        agregarInicio(eventos, "VINCULACION", vinculacion.getId(), vinculacion.getFechaInicio(), vinculacion.getEstado());
        agregarDocumentos(eventos, vinculacion.getEstudiante() == null ? null : vinculacion.getEstudiante().getId(), "VINCULACION");
        agregarBitacoras(eventos, bitacoraRepository.findByVinculacionId(vinculacion.getId()));
        agregarAsistencias(eventos, asistenciaRepository.findByVinculacionId(vinculacion.getId()));
        agregarNotasCoordinacion(eventos, notaCoordinacionRepository.findByVinculacionId(vinculacion.getId()));
        agregarAuditoriaNotas(eventos, null, vinculacion.getId());
        agregarEncuestas(eventos, encuestaRepository.findByVinculacionId(vinculacion.getId()));
        agregarFinalizacion(eventos, vinculacion.getEstado(), vinculacion.getMotivoFinalizacion(),
                vinculacion.getCerradoEn(), vinculacion.getCerradoPor(), "VINCULACION");
        return respuesta("VINCULACION", vinculacion.getId(), vinculacion.getPeriodoAcademico(), vinculacion.getEstado(),
                vinculacion.getMotivoFinalizacion(), vinculacion.getCerradoEn(), eventos);
    }

    private LineaTiempoExpedienteResponse respuesta(String proceso, Integer expedienteId, String periodo,
                                                     String estado, String motivo, LocalDateTime finalizadoEn,
                                                     List<EventoLineaTiempo> eventos) {
        eventos.sort(Comparator.comparing(EventoLineaTiempo::fecha).thenComparing(EventoLineaTiempo::clave));
        return new LineaTiempoExpedienteResponse(proceso, expedienteId, periodo, estado, motivo, finalizadoEn,
                List.copyOf(eventos));
    }

    private void agregarInicio(List<EventoLineaTiempo> eventos, String proceso, Integer id,
                               LocalDate fecha, String estado) {
        if (fecha == null) return;
        eventos.add(new EventoLineaTiempo(
                proceso.toLowerCase(Locale.ROOT) + "-inicio",
                "INICIO",
                "Inicio del proceso",
                "Inicio registrado del expediente académico.",
                fecha.atStartOfDay(), estado, null));
    }

    private void agregarDocumentos(List<EventoLineaTiempo> eventos, Integer estudianteId, String proceso) {
        if (estudianteId == null) return;
        documentoRepository.findByEstudianteId(estudianteId).stream()
                .filter(documento -> aplicaProceso(documento.getProceso(), proceso))
                .forEach(documento -> {
                    if (documento.getFechaSubida() != null) {
                        eventos.add(new EventoLineaTiempo(
                                "documento-" + documento.getId() + "-carga",
                                "DOCUMENTO_CARGADO",
                                "Documento cargado",
                                "Se cargó el documento '" + nombreDocumento(documento) + "'.",
                                documento.getFechaSubida(), documento.getEstado(), null));
                    }
                    if (documento.getFechaRevision() != null) {
                        eventos.add(new EventoLineaTiempo(
                                "documento-" + documento.getId() + "-revision",
                                "DOCUMENTO_REVISADO",
                                "Documento revisado",
                                "Se revisó el documento '" + nombreDocumento(documento) + "'.",
                                documento.getFechaRevision(), documento.getEstado(), nombre(documento.getRevisadoPor())));
                    }
                });
    }

    private void agregarBitacoras(List<EventoLineaTiempo> eventos, List<Bitacora> bitacoras) {
        bitacoras.forEach(bitacora -> {
            if (bitacora.getFecha() != null) {
                eventos.add(new EventoLineaTiempo(
                        "bitacora-" + bitacora.getId() + "-registro",
                        "BITACORA_REGISTRADA",
                        "Bitácora registrada",
                        "Se registró la bitácora del parcial " + bitacora.getParcial() + ".",
                        bitacora.getFecha().atStartOfDay(), bitacora.getEstado(), null));
            }
            if (bitacora.getFechaRevision() != null) {
                eventos.add(new EventoLineaTiempo(
                        "bitacora-" + bitacora.getId() + "-revision",
                        "BITACORA_REVISADA",
                        "Bitácora revisada",
                        "Se revisó la bitácora del parcial " + bitacora.getParcial() + ".",
                        bitacora.getFechaRevision(), bitacora.getEstado(), nombre(bitacora.getRevisadoPor())));
            }
        });
    }

    private void agregarNotasCoordinacion(List<EventoLineaTiempo> eventos, List<NotaCoordinacion> notas) {
        notas.stream()
                .filter(nota -> nota.getFechaCreacion() != null)
                .forEach(nota -> eventos.add(new EventoLineaTiempo(
                        "nota-coordinacion-" + nota.getId(),
                        "NOTA_COORDINACION",
                        "Nota de coordinación",
                        nota.getMensaje(),
                        nota.getFechaCreacion(), null, nombre(nota.getAutor()))));
    }

    private void agregarAsistencias(List<EventoLineaTiempo> eventos, List<Asistencia> asistencias) {
        asistencias.stream()
                .filter(asistencia -> asistencia.getFecha() != null)
                .forEach(asistencia -> eventos.add(new EventoLineaTiempo(
                        "asistencia-" + asistencia.getId(),
                        "ASISTENCIA_REGISTRADA",
                        "Asistencia registrada",
                        descripcionAsistencia(asistencia),
                        asistencia.getFecha().atStartOfDay(), asistencia.getEstado(), null)));
    }

    private String descripcionAsistencia(Asistencia asistencia) {
        String horario = asistencia.getHoraIngreso() == null || asistencia.getHoraSalida() == null
                ? ""
                : " Horario: " + asistencia.getHoraIngreso() + " - " + asistencia.getHoraSalida() + ".";
        return "Estado: " + (asistencia.getEstado() == null ? "Sin estado" : asistencia.getEstado()) + "." + horario;
    }

    private void agregarAuditoriaNotas(List<EventoLineaTiempo> eventos, Integer practicaId, Integer vinculacionId) {
        List<Auditoria> auditorias;
        if (practicaId != null) {
            auditorias = auditoriaRepository.findByTablaAfectadaYPracticaIdOrderByFechaAsc(
                    "EVALUACIONES_PRACTICAS_DETALLE", practicaId);
        } else if (vinculacionId != null) {
            auditorias = auditoriaRepository.findByTablaAfectadaYVinculacionIdOrderByFechaAsc(
                    "EVALUACIONES_PRACTICAS_DETALLE", vinculacionId);
        } else {
            auditorias = List.of();
        }
        auditorias.stream()
                .filter(auditoria -> auditoria.getFecha() != null)
                .forEach(auditoria -> eventos.add(new EventoLineaTiempo(
                        "auditoria-nota-" + auditoria.getId(),
                        "NOTA_AUDITADA",
                        "Nota registrada o actualizada",
                        "Se registró la auditoría de una nota del expediente.",
                        auditoria.getFecha(), auditoria.getAccion(), nombre(auditoria.getUsuario()))));
    }

    private void agregarEncuestas(List<EventoLineaTiempo> eventos, List<EncuestaSatisfaccion> encuestas) {
        encuestas.stream()
                .filter(encuesta -> encuesta.getFechaEnvio() != null)
                .forEach(encuesta -> eventos.add(new EventoLineaTiempo(
                        "encuesta-" + encuesta.getId(),
                        "ENCUESTA_ENVIADA",
                        "Encuesta enviada",
                        "Se envió la encuesta de satisfacción del parcial " + encuesta.getParcial() + ".",
                        encuesta.getFechaEnvio(), "enviada", nombre(encuesta.getEstudiante() == null
                        ? null : encuesta.getEstudiante().getUsuario()))));
    }

    private void agregarFinalizacion(List<EventoLineaTiempo> eventos, String estado, String motivo,
                                    LocalDateTime fecha, Usuario actor, String proceso) {
        if (fecha == null) return;
        boolean excepcional = "reprobado".equalsIgnoreCase(estado) || "retirado".equalsIgnoreCase(estado);
        eventos.add(new EventoLineaTiempo(
                proceso.toLowerCase(Locale.ROOT) + "-finalizacion",
                excepcional ? "FINALIZACION_EXCEPCIONAL" : "CIERRE_FORMAL",
                excepcional ? "Finalización excepcional" : "Cierre formal",
                excepcional ? "El expediente finalizó como " + estado.toUpperCase(Locale.ROOT)
                        + ". Motivo: " + Objects.toString(motivo, "")
                        : "El expediente fue cerrado formalmente.",
                fecha, estado, nombre(actor)));
    }

    private boolean aplicaProceso(String documentoProceso, String proceso) {
        return documentoProceso == null || documentoProceso.isBlank()
                || "GENERAL".equalsIgnoreCase(documentoProceso)
                || proceso.equalsIgnoreCase(documentoProceso);
    }

    private String nombreDocumento(DocEstudiante documento) {
        return documento.getTipoDocumento() == null ? "sin tipo" : documento.getTipoDocumento();
    }

    private String nombre(Usuario usuario) {
        if (usuario == null) return null;
        String nombre = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
        return nombre.isBlank() ? usuario.getEmail() : nombre;
    }
}
