package ec.edu.unibe.sistema_practicas.reporte;

import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteControllerSecurityTests {

    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private VacantePracticaRepository vacanteRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private BitacoraRepository bitacoraRepository;
    @Mock private AsistenciaRepository asistenciaRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks
    private ReporteController controller;

    @Test
    void reporte_excluye_practica_fuera_de_la_carrera_del_coordinador() {
        Usuario coordinador = usuario(7);
        Authentication authentication = autenticacion(coordinador);
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5);
        estudiante.setCarrera("Software");
        Practica practica = new Practica();
        practica.setId(10);
        practica.setEstudiante(estudiante);
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(Set.of("Derecho")));
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")).thenReturn(true);
        when(practicaRepository.findAll()).thenReturn(List.of(practica));

        assertTrue(controller.asignaciones(authentication, null, null, "PRACTICAS", null).isEmpty());
    }

    @Test
    void reporte_no_consulta_proceso_fuera_del_tipo_de_coordinacion() {
        Usuario coordinador = usuario(7);
        Authentication authentication = autenticacion(coordinador);
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(Set.of("Software")));
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")).thenReturn(false);

        assertTrue(controller.asignaciones(authentication, null, null, "PRACTICAS", null).isEmpty());
        verify(practicaRepository, never()).findAll();
    }

    private Usuario usuario(int id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Authentication autenticacion(Usuario usuario) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
