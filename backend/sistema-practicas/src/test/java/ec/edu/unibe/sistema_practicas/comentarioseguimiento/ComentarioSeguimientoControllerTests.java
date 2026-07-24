package ec.edu.unibe.sistema_practicas.comentarioseguimiento;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.coordinador.CoordinadorCarreraRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComentarioSeguimientoControllerTests {

    @Mock private ComentarioSeguimientoRepository comentarioRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private CoordinadorCarreraRepository coordinadorRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private NotificacionEmitter notificacionEmitter;
    @InjectMocks private ComentarioSeguimientoController controller;

    @Test
    void estudiante_escribe_solo_a_su_tutor_y_el_destinatario_se_resuelve_en_backend() {
        Usuario estudianteUsuario = usuario(1, "Ana", "Torres");
        Usuario tutor = usuario(2, "Luis", "Herrera");
        Practica practica = practica(10, estudianteUsuario, tutor, "en_curso");
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(comentarioRepository.save(any(ComentarioSeguimiento.class))).thenAnswer(inv -> {
            ComentarioSeguimiento comentario = inv.getArgument(0);
            comentario.setId(30);
            return comentario;
        });

        ComentarioSeguimientoResponse respuesta = controller.create(
                new ComentarioSeguimientoRequest(10, null, "TUTOR", "Necesito confirmar mi horario."),
                auth(estudianteUsuario, "ESTUDIANTE"), estudianteUsuario);

        assertEquals(30, respuesta.id());
        assertEquals("TUTOR", respuesta.audiencia());
        verify(notificacionEmitter).emitir(eq(tutor), eq("nota_coordinacion"),
                eq("Nuevo comentario de seguimiento"), any(), eq("comentario_seguimiento"), eq(30L),
                eq("/dashboard/practicas"));
    }

    @Test
    void estudiante_no_puede_dirigir_comentario_a_coordinacion() {
        Usuario estudianteUsuario = usuario(1, "Ana", "Torres");
        assertThrows(ResponseStatusException.class, () -> controller.create(
                new ComentarioSeguimientoRequest(10, null, "COORDINACION", "Mensaje de prueba válido."),
                auth(estudianteUsuario, "ESTUDIANTE"), estudianteUsuario));
    }

    @Test
    void tutor_ajeno_no_lee_el_expediente() {
        Usuario estudianteUsuario = usuario(1, "Ana", "Torres");
        Usuario tutorAsignado = usuario(2, "Luis", "Herrera");
        Usuario tutorAjeno = usuario(3, "Otro", "Tutor");
        when(practicaRepository.findById(10))
                .thenReturn(Optional.of(practica(10, estudianteUsuario, tutorAsignado, "en_curso")));

        assertThrows(ResponseStatusException.class,
                () -> controller.getByPractica(10, auth(tutorAjeno, "TUTOR"), tutorAjeno));
    }

    @Test
    void expediente_finalizado_conserva_historial_pero_no_admite_nuevos_comentarios() {
        Usuario estudianteUsuario = usuario(1, "Ana", "Torres");
        Usuario tutor = usuario(2, "Luis", "Herrera");
        when(practicaRepository.findById(10))
                .thenReturn(Optional.of(practica(10, estudianteUsuario, tutor, "completado")));

        assertThrows(IllegalArgumentException.class, () -> controller.create(
                new ComentarioSeguimientoRequest(10, null, "TUTOR", "Mensaje posterior al cierre."),
                auth(estudianteUsuario, "ESTUDIANTE"), estudianteUsuario));
    }

    private Practica practica(int id, Usuario estudianteUsuario, Usuario tutor, String estado) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setCarrera("Derecho");
        estudiante.setUsuario(estudianteUsuario);
        Practica practica = new Practica();
        practica.setId(id);
        practica.setEstudiante(estudiante);
        practica.setTutor(tutor);
        practica.setEstado(estado);
        return practica;
    }

    private Usuario usuario(int id, String nombre, String apellido) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setActivo(true);
        return usuario;
    }

    private Authentication auth(Usuario usuario, String rol) {
        return new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
