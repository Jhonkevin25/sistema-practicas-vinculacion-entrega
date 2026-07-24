package ec.edu.unibe.sistema_practicas.fundacion;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundacionControllerTests {

    @Mock private FundacionRepository fundacionRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;

    @InjectMocks private FundacionController controller;

    @Test
    void coordinador_ve_solo_fundaciones_con_convenio_compatible() {
        Authentication coordinador = autenticacionCoordinador();
        Set<String> carreras = Set.of("Derecho");
        Set<Integer> ids = Set.of(10, 11);
        List<Fundacion> visibles = List.of(fundacion(10), fundacion(11));
        prepararCoordinador(coordinador, carreras);
        when(convenioVigenteComponent.fundacionesVisiblesPara(carreras)).thenReturn(ids);
        when(fundacionRepository.findAllById(ids)).thenReturn(visibles);

        assertEquals(visibles, controller.getAll(coordinador));
    }

    @Test
    void coordinador_no_accede_por_id_a_fundacion_fuera_de_alcance() {
        Authentication coordinador = autenticacionCoordinador();
        Set<String> carreras = Set.of("Derecho");
        prepararCoordinador(coordinador, carreras);
        when(convenioVigenteComponent.fundacionesVisiblesPara(carreras)).thenReturn(Set.of(10));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getById(99, coordinador));

        assertEquals(403, error.getStatusCode().value());
        verify(fundacionRepository, never()).findById(99);
    }

    private void prepararCoordinador(Authentication authentication, Set<String> carreras) {
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(carreras));
        when(alcanceCoordinador.procesoVisible(authentication, "VINCULACION")).thenReturn(true);
    }

    private Fundacion fundacion(Integer id) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(id);
        return fundacion;
    }

    private Authentication autenticacionCoordinador() {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
