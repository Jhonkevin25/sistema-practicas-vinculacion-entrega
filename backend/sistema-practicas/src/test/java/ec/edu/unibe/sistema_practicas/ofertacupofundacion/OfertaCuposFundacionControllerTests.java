package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
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
class OfertaCuposFundacionControllerTests {

    @Mock private OfertaCuposFundacionRepository ofertaRepository;
    @Mock private OfertaCuposFundacionComponent ofertaComponent;
    @Mock private FundacionRepository fundacionRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks private OfertaCuposFundacionController controller;

    @Test
    void coordinador_ve_solo_ofertas_de_fundaciones_compatibles() {
        Authentication coordinador = autenticacionCoordinador();
        OfertaCuposFundacion visible = oferta(1, 10);
        OfertaCuposFundacion oculta = oferta(2, 20);
        when(alcanceCoordinador.procesoVisible(coordinador, "VINCULACION")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(coordinador))
                .thenReturn(Optional.of(Set.of("Derecho")));
        when(convenioVigenteComponent.fundacionesVisiblesPara(Set.of("Derecho")))
                .thenReturn(Set.of(10));
        when(ofertaRepository.findAll()).thenReturn(List.of(visible, oculta));
        when(ofertaComponent.enriquecerTodas(List.of(visible))).thenReturn(List.of(visible));

        assertEquals(List.of(visible), controller.getAll(null, coordinador));
    }

    @Test
    void coordinador_no_accede_por_id_a_oferta_fuera_de_alcance() {
        Authentication coordinador = autenticacionCoordinador();
        OfertaCuposFundacion oculta = oferta(2, 20);
        when(alcanceCoordinador.procesoVisible(coordinador, "VINCULACION")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(coordinador))
                .thenReturn(Optional.of(Set.of("Derecho")));
        when(convenioVigenteComponent.fundacionesVisiblesPara(Set.of("Derecho")))
                .thenReturn(Set.of(10));
        when(ofertaRepository.findById(2)).thenReturn(Optional.of(oculta));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getById(2, coordinador));

        assertEquals(403, error.getStatusCode().value());
        verify(ofertaComponent, never()).enriquecer(oculta);
    }

    private OfertaCuposFundacion oferta(Integer id, Integer fundacionId) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(fundacionId);
        fundacion.setNombre("Fundación " + fundacionId);
        OfertaCuposFundacion oferta = new OfertaCuposFundacion();
        oferta.setId(id);
        oferta.setFundacion(fundacion);
        return oferta;
    }

    private Authentication autenticacionCoordinador() {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
