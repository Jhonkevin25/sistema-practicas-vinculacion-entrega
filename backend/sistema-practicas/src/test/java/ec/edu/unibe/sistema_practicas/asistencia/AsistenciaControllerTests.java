package ec.edu.unibe.sistema_practicas.asistencia;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaControllerTests {

    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks
    private AsistenciaController controller;

    @Test
    void backend_resuelve_estudiante_desde_la_practica() {
        Usuario admin = usuario(1);
        Estudiante propietario = estudiante(8, "Software");
        Estudiante enviadoPorCliente = estudiante(99, "Derecho");
        Practica practica = practica(10, propietario, usuario(2));
        Asistencia request = asistencia(practica);
        request.setEstudiante(enviadoPorCliente);

        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(asistenciaRepository.existsByPracticaIdAndFecha(10, request.getFecha())).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> inv.getArgument(0));

        Asistencia guardada = controller.create(request, auth(admin, "ADMIN"), admin);

        assertEquals(8, guardada.getEstudiante().getId());
        assertEquals(10, guardada.getPractica().getId());
    }

    @Test
    void backend_resuelve_estudiante_desde_la_vinculacion() {
        Usuario admin = usuario(1);
        Estudiante propietario = estudiante(8, "Software");
        Estudiante enviadoPorCliente = estudiante(99, "Derecho");
        Vinculacion vinculacion = vinculacion(20, propietario, usuario(2));
        Asistencia request = new Asistencia();
        request.setVinculacion(vinculacion);
        request.setEstudiante(enviadoPorCliente);
        request.setFecha(LocalDate.now());
        request.setEstado("Presente");

        when(vinculacionRepository.findById(20)).thenReturn(Optional.of(vinculacion));
        when(asistenciaRepository.existsByVinculacionIdAndFecha(20, request.getFecha())).thenReturn(false);
        when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> inv.getArgument(0));

        Asistencia guardada = controller.create(request, auth(admin, "ADMIN"), admin);

        assertEquals(8, guardada.getEstudiante().getId());
        assertEquals(20, guardada.getVinculacion().getId());
    }

    @Test
    void tutor_no_registra_asistencia_en_practica_ajena() {
        Usuario tutorAsignado = usuario(2);
        Usuario otroTutor = usuario(3);
        Practica practica = practica(10, estudiante(8, "Software"), tutorAsignado);
        Asistencia request = asistencia(practica);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        assertThrows(ResponseStatusException.class,
                () -> controller.create(request, auth(otroTutor, "TUTOR"), otroTutor));

        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    void tutor_no_consulta_asistencias_de_practica_ajena() {
        Usuario tutorAsignado = usuario(2);
        Usuario otroTutor = usuario(3);
        Practica practica = practica(10, estudiante(8, "Software"), tutorAsignado);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        assertThrows(ResponseStatusException.class,
                () -> controller.getByPractica(10, auth(otroTutor, "TUTOR"), otroTutor));

        verify(asistenciaRepository, never()).findByPracticaId(10);
    }

    @Test
    void no_permite_duplicar_fecha_en_el_mismo_expediente() {
        Usuario tutor = usuario(2);
        Practica practica = practica(10, estudiante(8, "Software"), tutor);
        Asistencia request = asistencia(practica);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));
        when(asistenciaRepository.existsByPracticaIdAndFecha(10, request.getFecha())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> controller.create(request, auth(tutor, "TUTOR"), tutor));

        verify(asistenciaRepository, never()).save(any());
    }

    @Test
    void expediente_finalizado_no_acepta_asistencia() {
        Usuario tutor = usuario(2);
        Practica practica = practica(10, estudiante(8, "Software"), tutor);
        practica.setEstado("retirado");
        Asistencia request = asistencia(practica);
        when(practicaRepository.findById(10)).thenReturn(Optional.of(practica));

        assertThrows(IllegalArgumentException.class,
                () -> controller.create(request, auth(tutor, "TUTOR"), tutor));

        verify(asistenciaRepository, never()).save(any());
    }

    private Asistencia asistencia(Practica practica) {
        Asistencia asistencia = new Asistencia();
        asistencia.setPractica(practica);
        asistencia.setFecha(LocalDate.now());
        asistencia.setEstado("Presente");
        asistencia.setHoraIngreso("08:00");
        asistencia.setHoraSalida("12:00");
        return asistencia;
    }

    private Practica practica(int id, Estudiante estudiante, Usuario tutor) {
        Practica practica = new Practica();
        practica.setId(id);
        practica.setEstudiante(estudiante);
        practica.setTutor(tutor);
        practica.setEstado("en_curso");
        practica.setFechaInicio(LocalDate.now().minusDays(10));
        return practica;
    }

    private Vinculacion vinculacion(int id, Estudiante estudiante, Usuario tutor) {
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setId(id);
        vinculacion.setEstudiante(estudiante);
        vinculacion.setTutor(tutor);
        vinculacion.setEstado("en_curso");
        vinculacion.setFechaInicio(LocalDate.now().minusDays(10));
        return vinculacion;
    }

    private Estudiante estudiante(int id, String carrera) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        estudiante.setCarrera(carrera);
        return estudiante;
    }

    private Usuario usuario(int id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Authentication auth(Usuario usuario, String rol) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
