package ec.edu.unibe.sistema_practicas.estudiante;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstudianteControllerTests {

    @Mock private EstudianteRepository estudianteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks
    private EstudianteController controller;

    @Test
    void alta_rechaza_una_cuenta_inexistente() {
        Estudiante nuevo = estudiante(usuario(99));
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.create(nuevo));

        assertEquals("La cuenta de usuario seleccionada no existe.", error.getMessage());
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void alta_rechaza_una_cuenta_desactivada() {
        Usuario cuenta = usuarioEstudiante(20, false);
        Estudiante nuevo = estudiante(usuario(20));
        when(usuarioRepository.findById(20)).thenReturn(Optional.of(cuenta));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.create(nuevo));

        assertEquals("La cuenta de usuario seleccionada esta desactivada.", error.getMessage());
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void alta_rechaza_una_cuenta_sin_rol_estudiante() {
        Usuario cuenta = usuario(20);
        cuenta.setActivo(true);
        cuenta.setRoles(Set.of(rol("TUTOR")));
        Estudiante nuevo = estudiante(usuario(20));
        when(usuarioRepository.findById(20)).thenReturn(Optional.of(cuenta));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.create(nuevo));

        assertEquals("La cuenta seleccionada no tiene el rol ESTUDIANTE.", error.getMessage());
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void alta_guarda_la_cuenta_estudiante_cargada_desde_la_base() {
        Usuario cuentaAdministrada = usuarioEstudiante(20, true);
        Estudiante nuevo = estudiante(usuario(20));
        when(usuarioRepository.findById(20)).thenReturn(Optional.of(cuentaAdministrada));
        when(estudianteRepository.findByUsuarioId(20)).thenReturn(Optional.empty());
        when(estudianteRepository.save(nuevo)).thenReturn(nuevo);

        Estudiante guardado = controller.create(nuevo);

        assertSame(cuentaAdministrada, guardado.getUsuario());
        verify(estudianteRepository).save(nuevo);
    }

    @Test
    void coordinador_no_cambia_identidad_institucional_del_estudiante() {
        Usuario usuarioActual = usuario(20);
        Estudiante actual = estudiante(usuarioActual);
        Estudiante cambios = new Estudiante();
        cambios.setCarrera("Medicina");
        cambios.setUsuario(usuario(21));

        prepararCoordinador(actual);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.update(actual.getId(), cambios, autenticacionCoordinador()));

        assertEquals(403, error.getStatusCode().value());
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void coordinador_actualiza_semestre_sin_reenlazar_usuario_ni_carrera() {
        Usuario usuarioActual = usuario(20);
        Estudiante actual = estudiante(usuarioActual);
        Estudiante cambios = new Estudiante();
        cambios.setSemestre(6);
        cambios.setPeriodoAcademico("2026-2");

        prepararCoordinador(actual);
        when(estudianteRepository.save(actual)).thenReturn(actual);

        Estudiante guardado = controller.update(actual.getId(), cambios, autenticacionCoordinador())
                .getBody();

        assertSame(usuarioActual, guardado.getUsuario());
        assertEquals("Derecho", guardado.getCarrera());
        assertEquals(6, guardado.getSemestre());
        assertEquals("2026-2", guardado.getPeriodoAcademico());
    }

    private void prepararCoordinador(Estudiante estudiante) {
        when(estudianteRepository.findById(estudiante.getId())).thenReturn(Optional.of(estudiante));
        when(alcanceCoordinador.carrerasVisibles(any(Authentication.class)))
                .thenReturn(Optional.of(Set.of("Derecho")));
    }

    private Estudiante estudiante(Usuario usuario) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(8);
        estudiante.setUsuario(usuario);
        estudiante.setMatricula("EST-008");
        estudiante.setCarrera("Derecho");
        estudiante.setSemestre(5);
        return estudiante;
    }

    private Usuario usuario(Integer id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Usuario usuarioEstudiante(Integer id, boolean activo) {
        Usuario usuario = usuario(id);
        usuario.setActivo(activo);
        usuario.setRoles(Set.of(rol("ESTUDIANTE")));
        return usuario;
    }

    private Rol rol(String codigo) {
        Rol rol = new Rol();
        rol.setCodigo(codigo);
        return rol;
    }

    private Authentication autenticacionCoordinador() {
        return new UsernamePasswordAuthenticationToken(
                usuario(3), null, List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
