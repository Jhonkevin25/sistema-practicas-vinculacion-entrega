package ec.edu.unibe.sistema_practicas.convenio;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvenioVigenteComponentTests {

    @Mock
    private ConvenioRepository convenioRepository;

    @InjectMocks
    private ConvenioVigenteComponent convenioVigenteComponent;

    @Test
    void empresa_sin_convenio_vigente_rechaza_asignacion() {
        Empresa empresa = new Empresa();
        empresa.setId(10);
        LocalDate hoy = LocalDate.now();
        when(convenioRepository
                .findByEmpresaIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        10, "VIGENTE", hoy, hoy))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> convenioVigenteComponent.exigirParaEmpresa(empresa, "Ingeniería en Software"));
    }

    @Test
    void fundacion_con_convenio_de_otra_carrera_rechaza_asignacion() {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(20);
        Convenio convenio = new Convenio();
        convenio.setCarreras(new LinkedHashSet<>(List.of("Derecho")));
        LocalDate hoy = LocalDate.now();
        when(convenioRepository
                .findByFundacionIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        20, "VIGENTE", hoy, hoy))
                .thenReturn(List.of(convenio));

        assertThrows(IllegalArgumentException.class,
                () -> convenioVigenteComponent.exigirParaFundacion(fundacion, "Enfermería"));
    }

    @Test
    void convenio_sin_carreras_es_valido_para_todas() {
        Empresa empresa = new Empresa();
        empresa.setId(30);
        Convenio convenio = new Convenio();
        LocalDate hoy = LocalDate.now();
        when(convenioRepository
                .findByEmpresaIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        30, "VIGENTE", hoy, hoy))
                .thenReturn(List.of(convenio));

        assertDoesNotThrow(
                () -> convenioVigenteComponent.exigirParaEmpresa(empresa, "Ingeniería en Software"));
    }

    @Test
    void empresas_visibles_incluyen_convenio_general_y_carrera_del_coordinador() {
        LocalDate hoy = LocalDate.now();
        Convenio general = convenioEmpresa(1, Set.of());
        Convenio derecho = convenioEmpresa(2, Set.of("Derecho"));
        Convenio enfermeria = convenioEmpresa(3, Set.of("Enfermería"));
        when(convenioRepository
                .findByEmpresaIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        "VIGENTE", hoy, hoy))
                .thenReturn(List.of(general, derecho, enfermeria));

        Set<Integer> visibles = convenioVigenteComponent.empresasVisiblesPara(Set.of("Derecho"));

        assertEquals(Set.of(1, 2), visibles);
    }

    @Test
    void fundaciones_visibles_comparan_carreras_sin_depender_de_tildes() {
        LocalDate hoy = LocalDate.now();
        Convenio software = convenioFundacion(10, Set.of("Ingeniería en Software"));
        Convenio medicina = convenioFundacion(11, Set.of("Medicina"));
        when(convenioRepository
                .findByFundacionIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        "VIGENTE", hoy, hoy))
                .thenReturn(List.of(software, medicina));

        Set<Integer> visibles = convenioVigenteComponent.fundacionesVisiblesPara(
                Set.of("Ingenieria en Software"));

        assertEquals(Set.of(10), visibles);
    }

    private Convenio convenioEmpresa(Integer id, Set<String> carreras) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        Convenio convenio = new Convenio();
        convenio.setEmpresa(empresa);
        convenio.setCarreras(new LinkedHashSet<>(carreras));
        return convenio;
    }

    private Convenio convenioFundacion(Integer id, Set<String> carreras) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(id);
        Convenio convenio = new Convenio();
        convenio.setFundacion(fundacion);
        convenio.setCarreras(new LinkedHashSet<>(carreras));
        return convenio;
    }
}
