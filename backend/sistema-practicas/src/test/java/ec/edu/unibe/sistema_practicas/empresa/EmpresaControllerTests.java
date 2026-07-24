package ec.edu.unibe.sistema_practicas.empresa;

import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaControllerTests {

    @Mock private EmpresaRepository empresaRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;

    @InjectMocks private EmpresaController controller;

    @Test
    void admin_ve_todas_las_empresas() {
        Authentication admin = autenticacion("ADMIN");
        List<Empresa> empresas = List.of(empresa(1), empresa(2), empresa(3));
        when(alcanceCoordinador.carrerasVisibles(admin)).thenReturn(Optional.empty());
        when(empresaRepository.findAll()).thenReturn(empresas);

        assertEquals(empresas, controller.getAll(admin));
        verify(convenioVigenteComponent, never()).empresasVisiblesPara(any());
    }

    @Test
    void coordinador_ve_solo_empresas_con_convenio_compatible() {
        Authentication coordinador = autenticacion("COORDINADOR");
        Set<String> carreras = Set.of("Derecho");
        Set<Integer> ids = Set.of(1, 2);
        List<Empresa> visibles = List.of(empresa(1), empresa(2));
        prepararCoordinador(coordinador, carreras, true);
        when(convenioVigenteComponent.empresasVisiblesPara(carreras)).thenReturn(ids);
        when(empresaRepository.findAllById(ids)).thenReturn(visibles);

        assertEquals(visibles, controller.getAll(coordinador));
    }

    @Test
    void coordinador_de_vinculacion_no_ve_empresas() {
        Authentication coordinador = autenticacion("COORDINADOR");
        prepararCoordinador(coordinador, Set.of("Derecho"), false);

        assertEquals(List.of(), controller.getAll(coordinador));
        verify(empresaRepository, never()).findAll();
        verify(empresaRepository, never()).findAllById(any());
    }

    @Test
    void coordinador_no_accede_por_id_a_empresa_fuera_de_alcance() {
        Authentication coordinador = autenticacion("COORDINADOR");
        Set<String> carreras = Set.of("Derecho");
        prepararCoordinador(coordinador, carreras, true);
        when(convenioVigenteComponent.empresasVisiblesPara(carreras)).thenReturn(Set.of(1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getById(99, coordinador));

        assertEquals(403, error.getStatusCode().value());
        verify(empresaRepository, never()).findById(99);
    }

    private void prepararCoordinador(
            Authentication authentication,
            Set<String> carreras,
            boolean procesoVisible) {
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(carreras));
        when(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS"))
                .thenReturn(procesoVisible);
    }

    private Empresa empresa(Integer id) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        return empresa;
    }

    private Authentication autenticacion(String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
