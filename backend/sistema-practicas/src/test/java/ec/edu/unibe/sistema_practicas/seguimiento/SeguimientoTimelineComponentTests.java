package ec.edu.unibe.sistema_practicas.seguimiento;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaRepository;
import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccion;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccionRepository;
import ec.edu.unibe.sistema_practicas.notacoordinacion.NotaCoordinacionRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeguimientoTimelineComponentTests {

    @Mock private DocEstudianteRepository documentoRepository;
    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private AuditoriaRepository auditoriaRepository;
    @Mock private EncuestaSatisfaccionRepository encuestaRepository;
    @Mock private NotaCoordinacionRepository notaCoordinacionRepository;

    @InjectMocks
    private SeguimientoTimelineComponent component;

    @Test
    void linea_de_tiempo_usa_fechas_reales_y_orden_cronologico() {
        Practica practica = new Practica();
        practica.setId(10);
        practica.setPeriodoAcademico("2026-1");
        practica.setEstado("completado");
        practica.setFechaInicio(LocalDate.of(2026, 1, 10));
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7);
        practica.setEstudiante(estudiante);

        DocEstudiante documento = new DocEstudiante();
        documento.setId(3);
        documento.setTipoDocumento("cv");
        documento.setProceso("PRACTICAS");
        documento.setEstado("aprobado");
        documento.setFechaSubida(LocalDateTime.of(2026, 1, 11, 9, 0));

        Bitacora bitacora = new Bitacora();
        bitacora.setId(4);
        bitacora.setParcial(1);
        bitacora.setEstado("aprobada");
        bitacora.setFecha(LocalDate.of(2026, 1, 12));

        Asistencia asistencia = new Asistencia();
        asistencia.setId(6);
        asistencia.setFecha(LocalDate.of(2026, 1, 12));
        asistencia.setEstado("Presente");
        asistencia.setHoraIngreso("08:00");
        asistencia.setHoraSalida("12:00");

        EncuestaSatisfaccion encuesta = new EncuestaSatisfaccion();
        encuesta.setId(5);
        encuesta.setParcial(1);
        encuesta.setFechaEnvio(LocalDateTime.of(2026, 1, 13, 12, 0));

        when(documentoRepository.findByEstudianteId(7)).thenReturn(List.of(documento));
        when(bitacoraRepository.findByPracticaId(10)).thenReturn(List.of(bitacora));
        when(asistenciaRepository.findByPracticaId(10)).thenReturn(List.of(asistencia));
        when(auditoriaRepository.findByTablaAfectadaYPracticaIdOrderByFechaAsc("EVALUACIONES_PRACTICAS_DETALLE", 10))
                .thenReturn(List.of());
        when(encuestaRepository.findByPracticaId(10)).thenReturn(List.of(encuesta));

        LineaTiempoExpedienteResponse respuesta = component.paraPractica(practica);

        assertEquals("PRACTICAS", respuesta.proceso());
        assertEquals("completado", respuesta.estado());
        assertEquals(List.of("INICIO", "DOCUMENTO_CARGADO", "ASISTENCIA_REGISTRADA",
                        "BITACORA_REGISTRADA", "ENCUESTA_ENVIADA"),
                respuesta.eventos().stream().map(EventoLineaTiempo::tipo).toList());
        assertEquals(LocalDateTime.of(2026, 1, 10, 0, 0), respuesta.eventos().get(0).fecha());
        assertEquals(LocalDateTime.of(2026, 1, 13, 12, 0), respuesta.eventos().get(4).fecha());
    }
}
