package ec.edu.unibe.sistema_practicas.convenio;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConvenioControllerTests {

    @Mock private ConvenioRepository convenioRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private FundacionRepository fundacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;

    @InjectMocks private ConvenioController controller;

    @Test
    void coordinador_solo_consulta_convenios_de_entidades_visibles() {
        Authentication coordinador = autenticacion("COORDINADOR");
        Set<String> carreras = Set.of("Derecho");
        Convenio visible = convenioFundacion(10);
        Convenio oculto = convenioFundacion(11);
        Convenio empresaOculta = convenioEmpresa(20);
        when(alcanceCoordinador.carrerasVisibles(coordinador)).thenReturn(Optional.of(carreras));
        when(alcanceCoordinador.procesoVisible(coordinador, "PRACTICAS")).thenReturn(false);
        when(alcanceCoordinador.procesoVisible(coordinador, "VINCULACION")).thenReturn(true);
        when(convenioVigenteComponent.fundacionesVisiblesPara(carreras)).thenReturn(Set.of(10));
        when(convenioRepository.findAll()).thenReturn(List.of(visible, oculto, empresaOculta));

        assertEquals(List.of(visible), controller.getAll(coordinador));
    }

    @Test
    void coordinador_no_consulta_historial_de_fundacion_oculta() {
        Authentication coordinador = autenticacion("COORDINADOR");
        Set<String> carreras = Set.of("Derecho");
        when(alcanceCoordinador.carrerasVisibles(coordinador)).thenReturn(Optional.of(carreras));
        when(alcanceCoordinador.procesoVisible(coordinador, "PRACTICAS")).thenReturn(false);
        when(alcanceCoordinador.procesoVisible(coordinador, "VINCULACION")).thenReturn(true);
        when(convenioVigenteComponent.fundacionesVisiblesPara(carreras)).thenReturn(Set.of(10));

        assertEquals(List.of(), controller.getByFundacion(11, coordinador));
        verify(convenioRepository, never()).findByFundacionIdOrderByFechaFinDesc(11);
    }

    @Test
    void admin_conserva_acceso_a_todos_los_convenios() {
        Authentication admin = autenticacion("ADMIN");
        List<Convenio> convenios = List.of(convenioFundacion(10), convenioEmpresa(20));
        when(alcanceCoordinador.carrerasVisibles(admin)).thenReturn(Optional.empty());
        when(convenioRepository.findAll()).thenReturn(convenios);

        assertEquals(convenios, controller.getAll(admin));
    }

    private Convenio convenioFundacion(Integer id) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(id);
        Convenio convenio = new Convenio();
        convenio.setFundacion(fundacion);
        return convenio;
    }

    private Convenio convenioEmpresa(Integer id) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        Convenio convenio = new Convenio();
        convenio.setEmpresa(empresa);
        return convenio;
    }

    private Authentication autenticacion(String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
