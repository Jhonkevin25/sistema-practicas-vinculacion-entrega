package ec.edu.unibe.sistema_practicas.vacante;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacantePracticaControllerTests {

    @Mock private VacantePracticaRepository vacantePracticaRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private OfertaCuposEmpresaComponent ofertaCuposEmpresaComponent;

    @InjectMocks
    private VacantePracticaController controller;

    @Test
    void create_publica_vacante_con_empresa_carrera_y_convenio_validos() {
        VacantePractica vacante = vacanteValida();
        Empresa empresa = empresaActiva();
        Carrera carrera = carreraActiva();
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresa));
        when(carreraRepository.findByNombreIgnoreCase("Ingeniería en Software"))
                .thenReturn(Optional.of(carrera));
        when(vacantePracticaRepository.save(vacante)).thenAnswer(invocation -> {
            VacantePractica guardada = invocation.getArgument(0);
            guardada.setId(50);
            return guardada;
        });
        when(vacantePracticaRepository.findById(50)).thenReturn(Optional.of(vacante));

        VacantePractica creada = controller.create(vacante, null);

        assertEquals(50, creada.getId());
        assertEquals(empresa, creada.getEmpresa());
        assertEquals("Ingeniería en Software", creada.getCarrera());
        verify(convenioVigenteComponent).exigirParaEmpresa(empresa, "Ingeniería en Software");
        // Fase 42: al crear no hay vacante propia que excluir de la oferta
        verify(ofertaCuposEmpresaComponent).exigirDisponibilidad(
                1, "2026-2", "Ingeniería en Software", 2, null);
    }

    @Test
    void create_rechaza_empresa_sin_convenio_para_la_carrera() {
        VacantePractica vacante = vacanteValida();
        Empresa empresa = empresaActiva();
        Carrera carrera = carreraActiva();
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresa));
        when(carreraRepository.findByNombreIgnoreCase("Ingeniería en Software"))
                .thenReturn(Optional.of(carrera));
        when(convenioVigenteComponent.exigirParaEmpresa(empresa, "Ingeniería en Software"))
                .thenThrow(new IllegalArgumentException("La empresa no tiene un convenio vigente."));

        assertThrows(IllegalArgumentException.class, () -> controller.create(vacante, null));

        verify(vacantePracticaRepository, never()).save(any(VacantePractica.class));
    }

    @Test
    void create_rechaza_carrera_inactiva() {
        VacantePractica vacante = vacanteValida();
        Carrera carrera = carreraActiva();
        carrera.setActivo(false);
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresaActiva()));
        when(carreraRepository.findByNombreIgnoreCase("Ingeniería en Software"))
                .thenReturn(Optional.of(carrera));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> controller.create(vacante, null));

        assertEquals("La carrera seleccionada está inactiva.", error.getMessage());
        verify(vacantePracticaRepository, never()).save(any(VacantePractica.class));
    }

    @Test
    void create_rechaza_modalidad_de_vinculacion_en_vacante_de_practicas() {
        VacantePractica vacante = vacanteValida();
        vacante.setModalidadAcademica("Vinculación");
        Empresa empresa = empresaActiva();
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresa));
        when(carreraRepository.findByNombreIgnoreCase("Ingeniería en Software"))
                .thenReturn(Optional.of(carreraActiva()));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> controller.create(vacante, null));

        assertEquals("La modalidad académica debe ser Práctica I o Práctica II.", error.getMessage());
        verify(vacantePracticaRepository, never()).save(any(VacantePractica.class));
    }

    @Test
    void create_rechaza_cupos_superiores_a_la_oferta_disponible() {
        VacantePractica vacante = vacanteValida();
        Empresa empresa = empresaActiva();
        when(empresaRepository.findById(1)).thenReturn(Optional.of(empresa));
        when(carreraRepository.findByNombreIgnoreCase("Ingeniería en Software"))
                .thenReturn(Optional.of(carreraActiva()));
        when(ofertaCuposEmpresaComponent.exigirDisponibilidad(
                1, "2026-2", "Ingeniería en Software", 2, null))
                .thenThrow(new IllegalArgumentException("La empresa solo tiene 1 cupo disponible."));

        assertThrows(IllegalArgumentException.class, () -> controller.create(vacante, null));

        verify(vacantePracticaRepository, never()).save(any(VacantePractica.class));
    }

    private VacantePractica vacanteValida() {
        Empresa referenciaEmpresa = new Empresa();
        referenciaEmpresa.setId(1);
        VacantePractica vacante = new VacantePractica();
        vacante.setNombre("Desarrollo de portal institucional");
        vacante.setEmpresa(referenciaEmpresa);
        vacante.setCupos(2);
        vacante.setHoras(240);
        vacante.setDescripcion("Implementar y documentar módulos web.");
        vacante.setCarrera("Ingeniería en Software");
        vacante.setModalidadAcademica("Práctica I");
        vacante.setArea("Desarrollo web");
        vacante.setCiudad("Quito");
        vacante.setTipoEmpresa("Privada");
        vacante.setModalidadTrabajo("Híbrida");
        vacante.setFechaLimite(LocalDate.now().plusDays(30));
        vacante.setPerfilRequerido("Conocimientos básicos de desarrollo web.");
        vacante.setPeriodoAcademico("2026-2");
        return vacante;
    }

    private Empresa empresaActiva() {
        Empresa empresa = new Empresa();
        empresa.setId(1);
        empresa.setActivo(true);
        empresa.setNombre("Empresa de prueba");
        return empresa;
    }

    private Carrera carreraActiva() {
        Carrera carrera = new Carrera();
        carrera.setId(1);
        carrera.setNombre("Ingeniería en Software");
        carrera.setActivo(true);
        return carrera;
    }
}
