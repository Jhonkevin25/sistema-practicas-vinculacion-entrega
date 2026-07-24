package ec.edu.unibe.sistema_practicas.bitacora;

import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BitacoraControllerTests {

    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private CierreExpedienteComponent cierreExpedienteComponent;
    @Mock private HorasExpedienteComponent horasExpedienteComponent;

    @InjectMocks private BitacoraController controller;

    @Test
    void estudiante_propietario_corrige_y_reenvia_la_misma_bitacora() {
        Usuario usuario = usuario(1, "ana@est.unibe.edu.ec");
        Estudiante estudiante = estudiante(5, usuario);
        Bitacora original = bitacora(10, estudiante, "requiere_correccion");
        Usuario tutor = usuario(2, "tutor@unibe.edu.ec");
        original.setRevisadoPor(tutor);
        original.setFechaRevision(LocalDateTime.now());
        original.setObservacionesTutor("Aclara las actividades realizadas.");

        when(estudianteRepository.findByUsuarioEmail(usuario.getEmail())).thenReturn(Optional.of(estudiante));
        when(bitacoraRepository.findById(10)).thenReturn(Optional.of(original));
        when(bitacoraRepository.save(any(Bitacora.class))).thenAnswer(inv -> inv.getArgument(0));

        Bitacora guardada = controller.reenviarCorreccion(10,
                new ReenvioBitacoraRequest(LocalDate.of(2026, 7, 20),
                        "Actividad corregida", 6, "Evidencia ampliada"),
                autenticacionEstudiante(usuario), usuario).getBody();

        assertEquals(10, guardada.getId());
        assertEquals(1, guardada.getParcial());
        assertEquals("pendiente", guardada.getEstado());
        assertEquals("Actividad corregida", guardada.getActividad());
        assertEquals("Aclara las actividades realizadas.", guardada.getObservacionesTutor());
        assertNull(guardada.getRevisadoPor());
        assertNull(guardada.getFechaRevision());
        verify(bitacoraRepository).save(original);
    }

    @Test
    void estudiante_no_reenvia_bitacora_ajena() {
        Usuario ana = usuario(1, "ana@est.unibe.edu.ec");
        Usuario otroUsuario = usuario(3, "otro@est.unibe.edu.ec");
        Estudiante propietaria = estudiante(5, ana);
        Estudiante otro = estudiante(8, otroUsuario);
        when(estudianteRepository.findByUsuarioEmail(otroUsuario.getEmail())).thenReturn(Optional.of(otro));
        when(bitacoraRepository.findById(10)).thenReturn(Optional.of(
                bitacora(10, propietaria, "requiere_correccion")));

        assertThrows(ResponseStatusException.class, () -> controller.reenviarCorreccion(10,
                new ReenvioBitacoraRequest(LocalDate.now(), "Intento ajeno", 4, null),
                autenticacionEstudiante(otroUsuario), otroUsuario));
        verify(bitacoraRepository, never()).save(any());
    }

    @Test
    void bitacora_rechazada_no_se_puede_reenviar_como_correccion() {
        Usuario usuario = usuario(1, "ana@est.unibe.edu.ec");
        Estudiante estudiante = estudiante(5, usuario);
        when(estudianteRepository.findByUsuarioEmail(usuario.getEmail())).thenReturn(Optional.of(estudiante));
        when(bitacoraRepository.findById(10)).thenReturn(Optional.of(bitacora(10, estudiante, "rechazada")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.reenviarCorreccion(10,
                        new ReenvioBitacoraRequest(LocalDate.now(), "Actividad", 4, null),
                        autenticacionEstudiante(usuario), usuario));

        assertTrue(error.getMessage().contains("requiere corrección"));
        verify(bitacoraRepository, never()).save(any());
    }

    @Test
    void correccion_historica_resuelta_no_se_puede_reabrir() {
        Usuario usuario = usuario(1, "ana@est.unibe.edu.ec");
        Estudiante estudiante = estudiante(5, usuario);
        Bitacora observada = bitacora(10, estudiante, "requiere_correccion");
        Bitacora aprobadaPosterior = bitacora(11, estudiante, "aprobada");
        when(estudianteRepository.findByUsuarioEmail(usuario.getEmail())).thenReturn(Optional.of(estudiante));
        when(bitacoraRepository.findById(10)).thenReturn(Optional.of(observada));
        when(bitacoraRepository.findByPracticaId(20)).thenReturn(List.of(observada, aprobadaPosterior));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.reenviarCorreccion(10,
                        new ReenvioBitacoraRequest(LocalDate.now(), "Actividad", 4, null),
                        autenticacionEstudiante(usuario), usuario));

        assertTrue(error.getMessage().contains("ya fue resuelta"));
        verify(bitacoraRepository, never()).save(any());
    }

    private Bitacora bitacora(int id, Estudiante estudiante, String estado) {
        Practica practica = new Practica();
        practica.setId(20);
        practica.setEstudiante(estudiante);
        practica.setEstado("en_curso");
        Bitacora bitacora = new Bitacora();
        bitacora.setId(id);
        bitacora.setEstudiante(estudiante);
        bitacora.setPractica(practica);
        bitacora.setParcial(1);
        bitacora.setFecha(LocalDate.of(2026, 7, 18));
        bitacora.setActividad("Actividad original");
        bitacora.setHoras(4);
        bitacora.setEstado(estado);
        return bitacora;
    }

    private Estudiante estudiante(int id, Usuario usuario) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        estudiante.setUsuario(usuario);
        return estudiante;
    }

    private Usuario usuario(int id, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        return usuario;
    }

    private UsernamePasswordAuthenticationToken autenticacionEstudiante(Usuario usuario) {
        return new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")));
    }
}
