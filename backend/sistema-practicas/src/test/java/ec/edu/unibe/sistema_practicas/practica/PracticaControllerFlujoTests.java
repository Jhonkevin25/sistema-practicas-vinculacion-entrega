package ec.edu.unibe.sistema_practicas.practica;

import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.bitacora.HorasExpedienteComponent;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EncuestaSatisfaccionRepository;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalle;
import ec.edu.unibe.sistema_practicas.evaluacion.EvaluacionPracticaDetalleRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.seguimiento.FinalizacionExcepcionalRequest;
import ec.edu.unibe.sistema_practicas.seguimiento.SeguimientoTimelineComponent;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticaControllerFlujoTests {

    @Mock private PracticaRepository practicaRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private VacantePracticaRepository vacantePracticaRepository;
    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private EvaluacionPracticaDetalleRepository evaluacionRepository;
    @Mock private EncuestaSatisfaccionRepository encuestaRepository;
    @Mock private CierreExpedienteComponent cierreExpedienteComponent;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private AuditoriaEmitter auditoriaEmitter;
    @Mock private ObjectMapper objectMapper;
    @Mock private SeguimientoTimelineComponent seguimientoTimelineComponent;
    @Mock private HorasExpedienteComponent horasExpedienteComponent;
    // Fase 42: descontarCupoVacante exige periodo activo antes de consumir cupos
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private TutorAsignacionComponent tutorAsignacionComponent;

    @InjectMocks
    private PracticaController controller;

    @Test
    void cierre_falla_si_no_se_completaron_las_horas() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 60);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        assertThrows(IllegalArgumentException.class,
                () -> controller.cerrar(10, autenticacionAdmin(admin), admin));

        verify(practicaRepository, never()).save(any());
        verify(cierreExpedienteComponent, never()).registrarCierrePractica(any(), any(), anyMap());
    }

    @Test
    void cierre_exitoso_exige_tres_parciales_y_registra_cierre_formal() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 100);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(bitacoraRepository.findByPracticaId(10)).thenReturn(List.of());
        when(evaluacionRepository.findByPracticaId(10)).thenReturn(evaluacionesCompletas());
        when(encuestaRepository.findByPracticaId(10)).thenReturn(List.of());
        when(practicaRepository.save(any(Practica.class))).thenAnswer(inv -> inv.getArgument(0));

        Practica cerrada = controller.cerrar(10, autenticacionAdmin(admin), admin);

        assertEquals("completado", cerrada.getEstado());
        verify(cierreExpedienteComponent).validarDocumentosCierre(practica.getEstudiante(), "PRACTICAS", null);
        verify(cierreExpedienteComponent).registrarCierrePractica(any(), any(), anyMap());
        verify(practicaRepository).save(practica);
    }

    @Test
    void cambio_de_tutor_genera_auditoria() {
        Usuario admin = usuario(1);
        Usuario tutorAnterior = usuario(2);
        Usuario tutorNuevo = usuario(3);
        Practica practica = practica(admin, 100, 20);
        practica.setTutor(tutorAnterior);
        Practica detalles = practica(admin, 100, 20);
        detalles.setTutor(tutorNuevo);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(tutorAsignacionComponent.exigirValido(
                tutorNuevo, "PRACTICAS", practica.getEmpresa(), practica.getEstudiante().getCarrera()))
                .thenReturn(tutorNuevo);
        when(practicaRepository.save(any(Practica.class))).thenAnswer(inv -> inv.getArgument(0));

        controller.update(10, detalles, autenticacionAdmin(admin), admin, null);

        verify(auditoriaEmitter).registrar(
                org.mockito.ArgumentMatchers.eq("PRACTICAS"),
                org.mockito.ArgumentMatchers.eq("CAMBIO_TUTOR"),
                org.mockito.ArgumentMatchers.eq(admin), anyMap(), anyMap());
    }

    @Test
    void estudiante_no_consulta_practica_ajena() {
        Usuario propietario = usuario(1);
        Practica practica = practica(propietario, 100, 20);
        Usuario otroUsuario = usuario(2);
        otroUsuario.setEmail("otro@estudiantes.edu.ec");
        Estudiante otroEstudiante = new Estudiante();
        otroEstudiante.setId(6);
        otroEstudiante.setUsuario(otroUsuario);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(estudianteRepository.findByUsuarioEmail(otroUsuario.getEmail()))
                .thenReturn(Optional.of(otroEstudiante));

        assertThrows(ResponseStatusException.class,
                () -> controller.getById(10, autenticacion(otroUsuario, "ESTUDIANTE"), otroUsuario));
    }

    @Test
    void coordinador_no_consulta_practica_fuera_de_su_carrera() {
        Usuario coordinador = usuario(9);
        Practica practica = practica(usuario(1), 100, 20);
        Authentication authentication = autenticacion(coordinador, "COORDINADOR");
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(Set.of("Derecho")));

        assertThrows(ResponseStatusException.class,
                () -> controller.getById(10, authentication, coordinador));
    }

    @Test
    void coordinador_de_vinculacion_no_lista_practicas() {
        Usuario coordinador = usuario(9);
        Authentication authentication = autenticacion(coordinador, "COORDINADOR");
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")).thenReturn(false);

        assertTrue(controller.getAll(authentication, coordinador).isEmpty());
        verify(practicaRepository, never()).findAll();
    }

    @Test
    void practica_cerrada_rechaza_edicion_sin_justificacion_admin() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 100);
        practica.setEstado("completado");
        Practica detalles = new Practica();
        detalles.setHorasCompletadas(1);
        Authentication authentication = autenticacionAdmin(admin);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        doThrow(new IllegalArgumentException("La justificación administrativa es obligatoria."))
                .when(cierreExpedienteComponent)
                .validarModificacionPermitida(practica, authentication, admin, null, "PRACTICAS", 10);

        assertThrows(IllegalArgumentException.class,
                () -> controller.update(10, detalles, authentication, admin, null));
        verify(practicaRepository, never()).save(any());
    }

    @Test
    void practica_activa_rechaza_edicion_manual_de_horas_completadas() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 20);
        Practica detalles = new Practica();
        detalles.setEstado(null);
        detalles.setHorasCompletadas(80);
        Authentication authentication = autenticacionAdmin(admin);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.update(10, detalles, authentication, admin, null));

        assertTrue(error.getMessage().contains("bitácoras aprobadas"));
        verify(practicaRepository, never()).save(any());
    }

    @Test
    void practica_activa_rechaza_cambiar_horas_requeridas_de_la_vacante() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 20);
        Practica detalles = new Practica();
        detalles.setEstado(null);
        detalles.setHorasRequeridas(80);
        detalles.setHorasCompletadas(20);
        Authentication authentication = autenticacionAdmin(admin);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.update(10, detalles, authentication, admin, null));

        assertTrue(error.getMessage().contains("provienen de la vacante"));
        verify(practicaRepository, never()).save(any());
    }

    @Test
    void asignacion_directa_usa_horas_definidas_en_la_vacante() {
        Usuario admin = usuario(1);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setUsuario(admin);
        estudiante.setCarrera("Software");
        Vinculacion vinculacionCompletada = new Vinculacion();
        vinculacionCompletada.setEstado("completado");
        Empresa empresa = new Empresa();
        empresa.setId(4);
        VacantePractica vacante = new VacantePractica();
        vacante.setId(7);
        vacante.setEmpresa(empresa);
        vacante.setCarrera("Software");
        vacante.setModalidadAcademica("Práctica I");
        vacante.setPeriodoAcademico("2026-2");
        vacante.setCupos(1);
        vacante.setHoras(180);
        Practica solicitud = new Practica();
        solicitud.setEstudiante(estudiante);
        solicitud.setVacante(vacante);
        Usuario tutor = usuario(2);
        solicitud.setTutor(tutor);
        solicitud.setHorasRequeridas(20);
        solicitud.setHorasCompletadas(0);

        when(practicaRepository.findByEstudianteId(5)).thenReturn(List.of());
        when(vinculacionRepository.findByEstudianteId(5)).thenReturn(List.of(vinculacionCompletada));
        when(vacantePracticaRepository.findByIdForUpdate(7)).thenReturn(Optional.of(vacante));
        when(estudianteRepository.findByIdForUpdate(5)).thenReturn(Optional.of(estudiante));
        when(tutorAsignacionComponent.exigirValido(tutor, "PRACTICAS", empresa, "Software"))
                .thenReturn(tutor);
        when(practicaRepository.save(any(Practica.class))).thenAnswer(inv -> {
            Practica guardada = inv.getArgument(0);
            guardada.setId(10);
            return guardada;
        });

        Practica creada = controller.create(solicitud, autenticacionAdmin(admin));

        assertEquals(180, creada.getHorasRequeridas());
        assertEquals(0, vacante.getCupos());
    }

    @Test
    void continuidad_crea_practica_dos_con_misma_empresa_y_origen_trazable() {
        Usuario admin = usuario(1);
        Usuario tutor = usuario(2);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setUsuario(admin);
        estudiante.setCarrera("Software");
        Empresa empresa = new Empresa();
        empresa.setId(4);

        Vinculacion vinculacionCompletada = new Vinculacion();
        vinculacionCompletada.setEstado("completado");
        Practica origen = practica(admin, 180, 180);
        origen.setId(11);
        origen.setEstado("completado");
        origen.setEmpresa(empresa);

        VacantePractica vacante = new VacantePractica();
        vacante.setId(8);
        vacante.setEmpresa(empresa);
        vacante.setCarrera("Software");
        vacante.setModalidadAcademica("Práctica II");
        vacante.setPeriodoAcademico("2026-2");
        vacante.setCupos(1);
        vacante.setHoras(180);

        Practica solicitud = new Practica();
        solicitud.setEstudiante(estudiante);
        solicitud.setVacante(vacante);
        solicitud.setTutor(tutor);
        solicitud.setTipoAsignacion("CONTINUIDAD");
        solicitud.setPracticaOrigenId(11);
        solicitud.setHorasRequeridas(180);
        solicitud.setHorasCompletadas(0);

        when(estudianteRepository.findByIdForUpdate(5)).thenReturn(Optional.of(estudiante));
        when(practicaRepository.findByEstudianteId(5)).thenReturn(List.of(origen));
        when(vinculacionRepository.findByEstudianteId(5)).thenReturn(List.of(vinculacionCompletada));
        when(vacantePracticaRepository.findByIdForUpdate(8)).thenReturn(Optional.of(vacante));
        when(practicaRepository.findById(11)).thenReturn(Optional.of(origen));
        when(tutorAsignacionComponent.exigirValido(tutor, "PRACTICAS", empresa, "Software"))
                .thenReturn(tutor);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"tipoAsignacion\":\"CONTINUIDAD\"}");
        when(practicaRepository.save(any(Practica.class))).thenAnswer(inv -> {
            Practica guardada = inv.getArgument(0);
            guardada.setId(12);
            return guardada;
        });

        Practica creada = controller.create(solicitud, autenticacionAdmin(admin));

        assertEquals("CONTINUIDAD", creada.getTipoAsignacion());
        assertEquals(11, creada.getPracticaOrigenId());
        assertEquals(empresa, creada.getEmpresa());
        assertEquals(0, vacante.getCupos());
    }

    @Test
    void finalizacion_reprobado_solo_se_permite_desde_en_curso() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 20);
        practica.setEstado("pendiente");
        when(practicaRepository.findByIdForUpdate(10)).thenReturn(Optional.of(practica));

        assertThrows(IllegalArgumentException.class,
                () -> controller.finalizarExcepcional(10,
                        new FinalizacionExcepcionalRequest("REPROBADO", "Motivo suficientemente claro"),
                        autenticacionAdmin(admin), admin));
        verify(practicaRepository, never()).save(any());
    }

    @Test
    void finalizacion_retirado_guarda_snapshot_auditoria_y_notifica() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 20);
        when(practicaRepository.findByIdForUpdate(10)).thenReturn(Optional.of(practica));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(practicaRepository.save(any(Practica.class))).thenAnswer(inv -> inv.getArgument(0));

        Practica finalizada = controller.finalizarExcepcional(10,
                new FinalizacionExcepcionalRequest("RETIRADO", "Motivo suficientemente claro"),
                autenticacionAdmin(admin), admin);

        assertEquals("retirado", finalizada.getEstado());
        assertEquals("Motivo suficientemente claro", finalizada.getMotivoFinalizacion());
        assertTrue(finalizada.getCerradoEn() != null);
        verify(auditoriaEmitter).registrar(org.mockito.ArgumentMatchers.eq("PRACTICAS"),
                org.mockito.ArgumentMatchers.eq("FINALIZACION_EXCEPCIONAL"),
                org.mockito.ArgumentMatchers.eq(admin), any(), any());
        verify(notificacionEmitter).emitir(org.mockito.ArgumentMatchers.eq(practica.getEstudiante().getUsuario()),
                org.mockito.ArgumentMatchers.eq("expediente_cerrado"),
                org.mockito.ArgumentMatchers.contains("finalizada"), any(),
                org.mockito.ArgumentMatchers.eq("practica"), org.mockito.ArgumentMatchers.eq(10L), any());
    }

    @Test
    void finalizacion_repetida_en_mismo_estado_es_idempotente() {
        Usuario admin = usuario(1);
        Practica practica = practica(admin, 100, 20);
        practica.setEstado("retirado");
        practica.setMotivoFinalizacion("Motivo ya registrado");
        when(practicaRepository.findByIdForUpdate(10)).thenReturn(Optional.of(practica));

        Practica resultado = controller.finalizarExcepcional(10,
                new FinalizacionExcepcionalRequest("RETIRADO", "Otro motivo válido"),
                autenticacionAdmin(admin), admin);

        assertEquals(practica, resultado);
        verify(practicaRepository, never()).save(any());
        verifyNoInteractions(auditoriaEmitter, notificacionEmitter);
    }

    @Test
    void coordinador_no_finaliza_expediente_fuera_de_su_alcance() {
        Usuario coordinador = usuario(9);
        Practica practica = practica(usuario(1), 100, 20);
        Authentication authentication = autenticacion(coordinador, "COORDINADOR");
        when(practicaRepository.findByIdForUpdate(10)).thenReturn(Optional.of(practica));
        when(estudianteRepository.findById(5)).thenReturn(Optional.of(practica.getEstudiante()));
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(Set.of("Derecho")));

        assertThrows(ResponseStatusException.class,
                () -> controller.finalizarExcepcional(10,
                        new FinalizacionExcepcionalRequest("RETIRADO", "Motivo suficientemente claro"),
                        authentication, coordinador));
        verify(practicaRepository, never()).save(any());
    }

    @Test
    void reintento_de_practica_rechaza_el_mismo_periodo_academico() {
        Usuario admin = usuario(1);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setUsuario(admin);
        estudiante.setCarrera("Software");

        Practica anterior = practica(admin, 100, 20);
        anterior.setEstado("retirado");
        anterior.setPeriodoAcademico("2026-1");

        VacantePractica vacante = new VacantePractica();
        vacante.setId(7);
        vacante.setCarrera("Software");
        vacante.setPeriodoAcademico("2026-1");

        Practica solicitud = new Practica();
        solicitud.setEstudiante(estudiante);
        solicitud.setVacante(vacante);
        solicitud.setHorasRequeridas(120);

        when(practicaRepository.findByEstudianteId(5)).thenReturn(List.of(anterior));
        when(vinculacionRepository.findByEstudianteId(5)).thenReturn(List.of());
        when(vacantePracticaRepository.findByIdForUpdate(7)).thenReturn(Optional.of(vacante));
        when(estudianteRepository.findByIdForUpdate(5)).thenReturn(Optional.of(estudiante));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.create(solicitud, autenticacionAdmin(admin)));

        assertTrue(error.getMessage().contains("otro periodo academico"));
        verify(practicaRepository, never()).save(any());
    }

    private Practica practica(Usuario estudianteUsuario, int requeridas, int completadas) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setUsuario(estudianteUsuario);
        estudiante.setCarrera("Software");
        Practica practica = new Practica();
        practica.setId(10);
        practica.setEstudiante(estudiante);
        practica.setEstado("en_curso");
        practica.setHorasRequeridas(requeridas);
        practica.setHorasCompletadas(completadas);
        return practica;
    }

    private List<EvaluacionPracticaDetalle> evaluacionesCompletas() {
        return java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(parcial -> {
            EvaluacionPracticaDetalle evaluacion = new EvaluacionPracticaDetalle();
            evaluacion.setParcial(parcial);
            evaluacion.setNotaTutor(9.0);
            evaluacion.setNotaCoord(9.0);
            evaluacion.setEncuestaCompletada(true);
            return evaluacion;
        }).toList();
    }

    private Usuario usuario(int id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Authentication autenticacionAdmin(Usuario usuario) {
        return autenticacion(usuario, "ADMIN");
    }

    private Authentication autenticacion(Usuario usuario, String rol) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
