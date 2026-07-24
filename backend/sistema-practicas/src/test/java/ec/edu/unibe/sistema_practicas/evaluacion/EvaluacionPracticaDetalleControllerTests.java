package ec.edu.unibe.sistema_practicas.evaluacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.configuracion.FechaLimiteCalificacionRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluacionPracticaDetalleControllerTests {

    @Mock private EvaluacionPracticaDetalleRepository evaluacionRepository;
    @Mock private EncuestaSatisfaccionRepository encuestaRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private FechaLimiteCalificacionRepository fechaLimiteRepository;
    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private CierreExpedienteComponent cierreExpedienteComponent;
    @Mock private AuditoriaEmitter auditoriaEmitter;

    @InjectMocks
    private EvaluacionPracticaDetalleController controller;

    @Test
    void modificar_nota_guarda_snapshot_de_auditoria() {
        Usuario admin = new Usuario();
        admin.setId(1);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setUsuario(admin);
        Practica practica = new Practica();
        practica.setId(10);
        practica.setEstudiante(estudiante);
        practica.setPeriodoAcademico("2026-1");
        EvaluacionPracticaDetalle existente = new EvaluacionPracticaDetalle();
        existente.setId(30);
        existente.setPractica(practica);
        existente.setParcial(1);
        existente.setNotaTutor(8.0);
        existente.setNotaCoord(7.0);
        existente.setNotaFinal(7.5);
        existente.setEncuestaCompletada(false);
        EvaluacionPracticaDetalle cambio = new EvaluacionPracticaDetalle();
        Practica referencia = new Practica();
        referencia.setId(10);
        cambio.setPractica(referencia);
        cambio.setParcial(1);
        cambio.setNotaCoord(9.0);

        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(evaluacionRepository.findByPracticaIdAndParcial(10, 1)).thenReturn(Optional.of(existente));
        when(evaluacionRepository.save(any(EvaluacionPracticaDetalle.class))).thenAnswer(inv -> inv.getArgument(0));

        EvaluacionPracticaDetalle guardada = controller.upsert(cambio,
                new UsernamePasswordAuthenticationToken(admin, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))), admin, null);

        assertEquals(8.5, guardada.getNotaFinal());
        verify(auditoriaEmitter).registrar(eq("EVALUACIONES_PRACTICAS_DETALLE"), eq("MODIFICAR_NOTA"),
                eq(admin), anyMap(), anyMap());
    }

    @Test
    void estudiante_no_marca_encuesta_de_practica_ajena() {
        Usuario propietario = new Usuario();
        propietario.setId(1);
        Estudiante estudiantePropietario = new Estudiante();
        estudiantePropietario.setId(5);
        estudiantePropietario.setUsuario(propietario);
        Practica practica = new Practica();
        practica.setId(10);
        practica.setEstudiante(estudiantePropietario);

        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(2);
        otroUsuario.setEmail("otro@estudiantes.edu.ec");
        Estudiante otroEstudiante = new Estudiante();
        otroEstudiante.setId(6);
        otroEstudiante.setUsuario(otroUsuario);
        var authentication = new UsernamePasswordAuthenticationToken(otroUsuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(estudianteRepository.findByUsuarioEmail(otroUsuario.getEmail()))
                .thenReturn(Optional.of(otroEstudiante));

        assertThrows(ResponseStatusException.class,
                () -> controller.marcarEncuesta(10, null, 1, null, authentication, otroUsuario));
    }
}
