package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.bitacora.HorasExpedienteComponent;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccionRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalle;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalleRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.proyecto.CuposProyectoComponent;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VinculacionControllerFlujoTests {

    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private EvaluacionPracticaDetalleRepository evaluacionRepository;
    @Mock private EncuestaSatisfaccionRepository encuestaRepository;
    @Mock private CierreExpedienteComponent cierreExpedienteComponent;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private AuditoriaEmitter auditoriaEmitter;
    @Mock private HorasExpedienteComponent horasExpedienteComponent;
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private CuposProyectoComponent cuposProyectoComponent;
    @Mock private TutorAsignacionComponent tutorAsignacionComponent;

    @InjectMocks
    private VinculacionController controller;

    @Test
    void vinculacion_completa_cierra_con_bitacora_tres_parciales_y_documentos() {
        Usuario admin = new Usuario();
        admin.setId(1);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(8);
        estudiante.setUsuario(admin);
        estudiante.setCarrera("Derecho");
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setId(20);
        vinculacion.setEstudiante(estudiante);
        vinculacion.setEstado("en_curso");
        vinculacion.setHorasRequeridas(80);
        vinculacion.setHorasCompletadas(80);
        Bitacora bitacora = new Bitacora();
        bitacora.setEstado("aprobada");

        when(vinculacionRepository.findById(20)).thenReturn(Optional.of(vinculacion));
        when(bitacoraRepository.findByVinculacionId(20)).thenReturn(List.of(bitacora));
        when(evaluacionRepository.findByVinculacionId(20)).thenReturn(evaluacionesCompletas());
        when(encuestaRepository.findByVinculacionId(20)).thenReturn(List.of());
        when(vinculacionRepository.save(any(Vinculacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Vinculacion cerrada = controller.cerrar(20, autenticacionAdmin(admin), admin);

        assertEquals("completado", cerrada.getEstado());
        verify(cierreExpedienteComponent).validarDocumentosCierre(estudiante, "VINCULACION", null);
        verify(cierreExpedienteComponent).registrarCierreVinculacion(any(), any(), anyMap());
        verify(vinculacionRepository).save(vinculacion);
    }

    @Test
    void vinculacion_activa_rechaza_edicion_manual_de_horas_completadas() {
        Usuario admin = new Usuario();
        admin.setId(1);
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setId(20);
        vinculacion.setEstado("en_curso");
        vinculacion.setHorasRequeridas(160);
        vinculacion.setHorasCompletadas(40);
        Vinculacion detalles = new Vinculacion();
        detalles.setEstado(null);
        detalles.setHorasCompletadas(100);
        Authentication authentication = autenticacionAdmin(admin);
        when(vinculacionRepository.findById(20)).thenReturn(Optional.of(vinculacion));

        assertThrows(IllegalArgumentException.class,
                () -> controller.update(20, detalles, authentication, admin, null));

        verify(vinculacionRepository, never()).save(any());
    }

    @Test
    void asignacion_directa_toma_periodo_y_horas_del_proyecto() {
        Usuario admin = new Usuario();
        admin.setId(1);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(8);
        estudiante.setUsuario(admin);
        estudiante.setCarrera("Derecho");
        Proyecto proyecto = new Proyecto();
        proyecto.setId(30);
        proyecto.setNombre("Orientación comunitaria");
        proyecto.setPeriodo("2026-2");
        proyecto.setHorasRequeridas(120);
        proyecto.setFundacion(new ec.edu.unibe.sistema_practicas.fundacion.Fundacion());
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setCodigo("2026-2");
        periodo.setEstado("ACTIVO");

        Vinculacion solicitud = new Vinculacion();
        solicitud.setEstudiante(estudiante);
        solicitud.setProyecto(proyecto);
        Usuario tutor = new Usuario();
        tutor.setId(9);
        solicitud.setTutor(tutor);
        solicitud.setPeriodoAcademico("2099-1");
        solicitud.setHorasRequeridas(999);
        solicitud.setHorasCompletadas(0);

        when(vinculacionRepository.findByEstudianteId(8)).thenReturn(List.of());
        when(practicaRepository.findByEstudianteId(8)).thenReturn(List.of());
        when(proyectoRepository.findByIdForUpdate(30)).thenReturn(Optional.of(proyecto));
        when(estudianteRepository.findByIdForUpdate(8)).thenReturn(Optional.of(estudiante));
        when(tutorAsignacionComponent.exigirValido(
                eq(tutor), eq("VINCULACION"), any(ec.edu.unibe.sistema_practicas.fundacion.Fundacion.class), eq("Derecho")))
                .thenReturn(tutor);
        when(periodoComponent.exigirActivo("2026-2")).thenReturn(periodo);
        when(vinculacionRepository.save(any(Vinculacion.class))).thenAnswer(inv -> {
            Vinculacion guardada = inv.getArgument(0);
            guardada.setId(40);
            return guardada;
        });

        Vinculacion creada = controller.create(solicitud, autenticacionAdmin(admin));

        assertEquals("2026-2", creada.getPeriodoAcademico());
        assertEquals(120, creada.getHorasRequeridas());
        assertEquals(0, creada.getHorasCompletadas());
        assertEquals("en_curso", creada.getEstado());
        verify(cuposProyectoComponent).descontar(proyecto, "Derecho");
        verify(convenioVigenteComponent)
                .exigirParaFundacionEnPeriodo(proyecto.getFundacion(), "Derecho", periodo);
    }

    private List<EvaluacionPracticaDetalle> evaluacionesCompletas() {
        return java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(parcial -> {
            EvaluacionPracticaDetalle evaluacion = new EvaluacionPracticaDetalle();
            evaluacion.setParcial(parcial);
            evaluacion.setNotaTutor(8.5);
            evaluacion.setNotaCoord(9.0);
            evaluacion.setEncuestaCompletada(true);
            return evaluacion;
        }).toList();
    }

    private Authentication autenticacionAdmin(Usuario usuario) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
