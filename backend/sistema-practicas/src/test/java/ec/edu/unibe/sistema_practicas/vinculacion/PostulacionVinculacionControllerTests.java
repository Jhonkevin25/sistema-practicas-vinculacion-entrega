package ec.edu.unibe.sistema_practicas.vinculacion;

import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostulacionVinculacionControllerTests {

    @Mock private PostulacionVinculacionRepository postulacionRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private DocEstudianteRepository documentoRepository;
    @Mock private FechasConvocatoriaRepository fechasRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private CuposProyectoComponent cuposProyectoComponent;
    @Mock private TutorAsignacionComponent tutorAsignacionComponent;

    @InjectMocks
    private PostulacionVinculacionController controller;

    @Test
    void reprocesar_postulacion_aprobada_no_descuenta_otro_cupo() {
        Usuario admin = new Usuario();
        admin.setId(1);
        Usuario tutor = new Usuario();
        tutor.setId(9);

        Estudiante estudiante = new Estudiante();
        estudiante.setId(3);
        estudiante.setCarrera("Derecho");
        estudiante.setUsuario(admin);

        Proyecto proyecto = new Proyecto();
        proyecto.setId(5);
        proyecto.setNombre("Consultorio jurídico");
        proyecto.setFundacion(new Fundacion());
        proyecto.setPeriodo("2026-2");
        proyecto.setHorasRequeridas(120);
        proyecto.setCuposTotales(1);
        proyecto.setCuposDisponibles(1);

        PostulacionVinculacion postulacion = new PostulacionVinculacion();
        postulacion.setId(10);
        postulacion.setEstudiante(estudiante);
        postulacion.setProyecto(proyecto);
        postulacion.setEstado("Pendiente");
        postulacion.setPeriodoAcademico("2026-2");

        Vinculacion request = new Vinculacion();
        request.setTutor(tutor);
        request.setHorasRequeridas(999);

        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setCodigo("2026-2");
        periodo.setEstado("ACTIVO");

        when(postulacionRepository.findByIdForUpdate(10)).thenReturn(Optional.of(postulacion));
        when(estudianteRepository.findByIdForUpdate(3)).thenReturn(Optional.of(estudiante));
        when(tutorAsignacionComponent.exigirValido(eq(tutor), eq("VINCULACION"), any(Fundacion.class), eq("Derecho")))
                .thenReturn(tutor);
        when(vinculacionRepository.findByEstudianteId(3)).thenReturn(List.of());
        when(practicaRepository.findByEstudianteId(3)).thenReturn(List.of());
        when(proyectoRepository.findByIdForUpdate(5)).thenReturn(Optional.of(proyecto));
        when(periodoComponent.exigirActivo("2026-2")).thenReturn(periodo);
        when(vinculacionRepository.save(org.mockito.ArgumentMatchers.any(Vinculacion.class))).thenAnswer(inv -> {
            Vinculacion vinculacion = inv.getArgument(0);
            vinculacion.setId(20);
            return vinculacion;
        });
        when(postulacionRepository.save(org.mockito.ArgumentMatchers.any(PostulacionVinculacion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        controller.aprobar(10, request, autenticacionAdmin(admin), admin);

        assertEquals("Aprobado", postulacion.getEstado());
        ArgumentCaptor<Vinculacion> vinculacionCaptor = ArgumentCaptor.forClass(Vinculacion.class);
        verify(vinculacionRepository).save(vinculacionCaptor.capture());
        assertEquals(120, vinculacionCaptor.getValue().getHorasRequeridas());
        assertEquals("2026-2", vinculacionCaptor.getValue().getPeriodoAcademico());
        assertThrows(IllegalArgumentException.class,
                () -> controller.aprobar(10, request, autenticacionAdmin(admin), admin));

        verify(postulacionRepository, times(2)).findByIdForUpdate(10);
        verify(postulacionRepository, never()).findById(10);
        verify(cuposProyectoComponent, times(1)).descontar(proyecto, "Derecho");
        verify(vinculacionRepository, times(1))
                .save(org.mockito.ArgumentMatchers.any(Vinculacion.class));
        verify(postulacionRepository, times(1)).save(postulacion);
    }

    private Authentication autenticacionAdmin(Usuario usuario) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
