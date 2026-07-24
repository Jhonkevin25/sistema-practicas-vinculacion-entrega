package ec.edu.unibe.sistema_practicas.proyecto;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.ofertacupofundacion.OfertaCuposFundacion;
import ec.edu.unibe.sistema_practicas.ofertacupofundacion.OfertaCuposFundacionComponent;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacionRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyectoControllerTests {

    @Mock private ProyectoRepository proyectoRepository;
    @Mock private FundacionRepository fundacionRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private PostulacionVinculacionRepository postulacionRepository;
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private OfertaCuposFundacionComponent ofertaComponent;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks
    private ProyectoController controller;

    @Test
    void crear_reserva_desde_oferta_e_ignora_disponibles_del_cliente() {
        Authentication auth = autenticacionAdmin();
        Fundacion fundacion = fundacion(1);
        Carrera carrera = carrera(4, "Derecho");
        PeriodoAcademico periodo = periodo("2026-2");
        OfertaCuposFundacion oferta = ofertaGeneral(fundacion, "2026-2", 5);
        Proyecto solicitud = solicitud(fundacion, carrera, 2);
        solicitud.setCuposDisponibles(99);

        when(alcanceCoordinador.procesoVisible(auth, "VINCULACION")).thenReturn(true);
        when(fundacionRepository.findById(1)).thenReturn(Optional.of(fundacion));
        when(periodoComponent.exigirConfigurable("2026-2")).thenReturn(periodo);
        when(carreraRepository.findById(4)).thenReturn(Optional.of(carrera));
        when(ofertaComponent.exigirDisponibilidad(1, "2026-2", 2, Map.of(), null))
                .thenReturn(oferta);
        when(proyectoRepository.save(any(Proyecto.class))).thenAnswer(inv -> inv.getArgument(0));

        Proyecto creado = controller.create(solicitud, auth);

        assertEquals(2, creado.getCuposTotales());
        assertEquals(2, creado.getCuposDisponibles());
        assertEquals(120, creado.getHorasRequeridas());
        assertEquals("PRESENCIAL", creado.getModalidad());
        assertEquals(1, creado.getCarreras().size());
        verify(convenioVigenteComponent)
                .exigirParaFundacionEnPeriodo(fundacion, "Derecho", periodo);
    }

    @Test
    void editar_rechaza_total_inferior_a_estudiantes_ya_asignados() {
        Authentication auth = autenticacionAdmin();
        Fundacion fundacion = fundacion(1);
        Carrera carrera = carrera(4, "Derecho");
        PeriodoAcademico periodo = periodo("2026-2");
        OfertaCuposFundacion oferta = ofertaGeneral(fundacion, "2026-2", 5);
        Proyecto existente = solicitud(fundacion, carrera, 4);
        existente.setId(7);
        existente.setCuposDisponibles(2);
        Proyecto cambios = solicitud(fundacion, carrera, 1);

        when(alcanceCoordinador.procesoVisible(auth, "VINCULACION")).thenReturn(true);
        when(proyectoRepository.findByIdForUpdate(7)).thenReturn(Optional.of(existente));
        when(periodoComponent.exigirConfigurable("2026-2")).thenReturn(periodo);
        when(carreraRepository.findById(4)).thenReturn(Optional.of(carrera));
        when(vinculacionRepository.findByProyectoId(7)).thenReturn(List.of(
                vinculacion(estudiante("Derecho")), vinculacion(estudiante("Derecho"))));
        when(ofertaComponent.exigirDisponibilidad(1, "2026-2", 1, Map.of(), 7))
                .thenReturn(oferta);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.update(7, cambios, auth));

        assertEquals("Los cupos totales no pueden ser menores que los 2 cupos ya ocupados del proyecto.",
                error.getMessage());
        verify(proyectoRepository, never()).save(any());
    }

    @Test
    void coordinador_ve_proyecto_si_participa_alguna_carrera_de_su_alcance() {
        Authentication auth = autenticacionCoordinador();
        Fundacion fundacion = fundacion(1);
        Proyecto mixto = solicitud(fundacion, carrera(4, "Derecho"), 2);
        agregarCarrera(mixto, carrera(5, "Ingeniería en Software"));
        Proyecto ajeno = solicitud(fundacion, carrera(6, "Enfermería"), 2);
        when(alcanceCoordinador.procesoVisible(auth, "VINCULACION")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(auth)).thenReturn(Optional.of(Set.of("Derecho")));
        when(proyectoRepository.findAll()).thenReturn(List.of(mixto, ajeno));

        assertEquals(List.of(mixto), controller.getAll(null, auth, null));
    }

    private Proyecto solicitud(Fundacion fundacion, Carrera carrera, int cupos) {
        Proyecto proyecto = new Proyecto();
        proyecto.setFundacion(fundacion);
        proyecto.setNombre("Orientación comunitaria");
        proyecto.setDescripcion("Atención y acompañamiento a la comunidad.");
        proyecto.setPeriodo("2026-2");
        proyecto.setHorasRequeridas(120);
        proyecto.setCiudad("Quito");
        proyecto.setModalidad("presencial");
        proyecto.setCuposTotales(cupos);
        proyecto.setEstado(true);
        ProyectoCarrera participacion = new ProyectoCarrera();
        participacion.setProyecto(proyecto);
        participacion.setCarrera(carrera);
        proyecto.getCarreras().add(participacion);
        return proyecto;
    }

    private void agregarCarrera(Proyecto proyecto, Carrera carrera) {
        ProyectoCarrera participacion = new ProyectoCarrera();
        participacion.setProyecto(proyecto);
        participacion.setCarrera(carrera);
        proyecto.getCarreras().add(participacion);
    }

    private OfertaCuposFundacion ofertaGeneral(Fundacion fundacion, String periodo, int cupos) {
        OfertaCuposFundacion oferta = new OfertaCuposFundacion();
        oferta.setFundacion(fundacion);
        oferta.setPeriodoAcademico(periodo);
        oferta.setDistribucion("GENERAL");
        oferta.setCuposTotales(cupos);
        oferta.setActivo(true);
        return oferta;
    }

    private PeriodoAcademico periodo(String codigo) {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setCodigo(codigo);
        periodo.setFechaInicio(LocalDate.of(2026, 7, 1));
        periodo.setFechaFin(LocalDate.of(2026, 12, 31));
        periodo.setEstado("ACTIVO");
        return periodo;
    }

    private Fundacion fundacion(Integer id) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(id);
        fundacion.setNombre("Fundación de prueba");
        fundacion.setActiva(true);
        return fundacion;
    }

    private Carrera carrera(Integer id, String nombre) {
        Carrera carrera = new Carrera();
        carrera.setId(id);
        carrera.setNombre(nombre);
        carrera.setActivo(true);
        return carrera;
    }

    private Estudiante estudiante(String carrera) {
        Estudiante estudiante = new Estudiante();
        estudiante.setCarrera(carrera);
        return estudiante;
    }

    private Vinculacion vinculacion(Estudiante estudiante) {
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setEstudiante(estudiante);
        return vinculacion;
    }

    private Authentication autenticacionAdmin() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication autenticacionCoordinador() {
        return new UsernamePasswordAuthenticationToken(
                "coordinador", null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
