package ec.edu.unibe.sistema_practicas.seguimiento;

import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionRepository;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Revisa a diario los expedientes activos y notifica al estudiante (sin que
// el coordinador tenga que hacerlo manualmente) cuando lleva demasiados dias
// sin registrar actividad. Mismo umbral (14 dias) que ya usa la alerta de
// "riesgo" que ve el coordinador en dashboard/seguimiento (PracticaController).
@Component
@RequiredArgsConstructor
public class AlertaAtrasoScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertaAtrasoScheduler.class);
    private static final List<String> ESTADOS_ACTIVOS = List.of("pendiente", "en_curso");
    private static final int DIAS_UMBRAL = 14;
    private static final int DIAS_ENTRE_ALERTAS = 7;

    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final BitacoraRepository bitacoraRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final NotificacionRepository notificacionRepository;
    private final NotificacionEmitter notificacionEmitter;

    @Scheduled(cron = "0 0 6 * * *")
    public synchronized void revisarAtrasos() {
        practicaRepository.findByEstadoIn(ESTADOS_ACTIVOS).forEach(this::revisarPracticaSeguro);
        vinculacionRepository.findByEstadoIn(ESTADOS_ACTIVOS).forEach(this::revisarVinculacionSeguro);
    }

    private void revisarPracticaSeguro(Practica practica) {
        try {
            revisarPractica(practica);
        } catch (Exception e) {
            log.warn("No se pudo evaluar el atraso de la practica {}: {}", practica.getId(), e.getMessage());
        }
    }

    private void revisarVinculacionSeguro(Vinculacion vinculacion) {
        try {
            revisarVinculacion(vinculacion);
        } catch (Exception e) {
            log.warn("No se pudo evaluar el atraso de la vinculacion {}: {}", vinculacion.getId(), e.getMessage());
        }
    }

    private void revisarPractica(Practica practica) {
        Estudiante estudiante = practica.getEstudiante();
        if (estudiante == null || practica.getId() == null) return;
        Integer dias = diasSinActividad(practica.getFechaInicio(),
                bitacoraRepository.findByPracticaId(practica.getId()),
                asistenciaRepository.findByPracticaId(practica.getId()));
        if (dias == null || dias < DIAS_UMBRAL) return;
        if (yaNotificadoReciente("practica", practica.getId().longValue())) return;

        notificacionEmitter.emitir(estudiante.getUsuario(), "expediente_atrasado",
                "Estás atrasado con tu seguimiento",
                "Llevas " + dias + " días sin registrar actividad en tu práctica. Registra tus bitácoras para mantener tu avance al día.",
                "practica", practica.getId().longValue(), "/dashboard/practicas");
    }

    private void revisarVinculacion(Vinculacion vinculacion) {
        Estudiante estudiante = vinculacion.getEstudiante();
        if (estudiante == null || vinculacion.getId() == null) return;
        Integer dias = diasSinActividad(vinculacion.getFechaInicio(),
                bitacoraRepository.findByVinculacionId(vinculacion.getId()),
                asistenciaRepository.findByVinculacionId(vinculacion.getId()));
        if (dias == null || dias < DIAS_UMBRAL) return;
        if (yaNotificadoReciente("vinculacion", vinculacion.getId().longValue())) return;

        notificacionEmitter.emitir(estudiante.getUsuario(), "expediente_atrasado",
                "Estás atrasado con tu seguimiento",
                "Llevas " + dias + " días sin registrar actividad en tu vinculación. Registra tus bitácoras para mantener tu avance al día.",
                "vinculacion", vinculacion.getId().longValue(), "/dashboard/vinculacion");
    }

    private boolean yaNotificadoReciente(String referenciaTipo, Long referenciaId) {
        return notificacionRepository.existsByReferenciaTipoAndReferenciaIdAndTipoAndFechaCreacionAfter(
                referenciaTipo, referenciaId, "expediente_atrasado",
                LocalDateTime.now().minusDays(DIAS_ENTRE_ALERTAS));
    }

    // Misma formula que PracticaController.diasSinActividad(): ultima fecha
    // entre bitacoras, asistencias o inicio del expediente, comparada con hoy.
    // Duplicada aqui porque el proyecto no tiene capa de servicio compartida
    // (package-by-feature); si se ajusta el umbral de riesgo alla, revisar aca.
    private Integer diasSinActividad(LocalDate fechaInicio, List<Bitacora> bitacoras, List<Asistencia> asistencias) {
        Optional<LocalDate> ultimaBitacora = bitacoras.stream()
                .map(Bitacora::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        Optional<LocalDate> ultimaAsistencia = asistencias.stream()
                .map(Asistencia::getFecha)
                .filter(fecha -> fecha != null)
                .max(Comparator.naturalOrder());
        LocalDate ultima = List.of(ultimaBitacora, ultimaAsistencia, Optional.ofNullable(fechaInicio)).stream()
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return ultima == null ? null : (int) ChronoUnit.DAYS.between(ultima, LocalDate.now());
    }
}
