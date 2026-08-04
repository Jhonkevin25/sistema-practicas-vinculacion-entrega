package ec.edu.unibe.sistema_practicas.tutorfundacion;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorFundacionControllerTests {

    @Mock private TutorFundacionRepository tutorFundacionRepository;
    @Mock private FundacionRepository fundacionRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private TutorAsignacionComponent tutorAsignacionComponent;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @InjectMocks private TutorFundacionController controller;

    @Test
    void devuelve_tutor_compatible_con_una_de_sus_varias_carreras() {
        Fundacion fundacion = fundacion(3);
        TutorFundacion vinculo = vinculo(fundacion, "Ingeniería en Software", "Derecho");
        when(fundacionRepository.findById(3)).thenReturn(Optional.of(fundacion));
        when(tutorFundacionRepository.findByFundacionIdAndActivoTrueOrderByNombreAsc(3))
                .thenReturn(List.of(vinculo));

        List<TutorFundacionResponse> resultado = controller.compatibles(3, "Derecho", auth("ADMIN"));

        assertEquals(1, resultado.size());
        assertEquals(8, resultado.get(0).tutorId());
    }

    @Test
    void coordinador_no_consulta_tutores_de_fundacion_fuera_de_su_alcance() {
        Fundacion fundacion = fundacion(4);
        Authentication authentication = auth("COORDINADOR");
        when(fundacionRepository.findById(4)).thenReturn(Optional.of(fundacion));
        when(alcanceCoordinador.procesoVisible(authentication, "VINCULACION")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(authentication)).thenReturn(Optional.of(Set.of("Derecho")));
        when(convenioVigenteComponent.fundacionesVisiblesPara(Set.of("Derecho"))).thenReturn(Set.of());

        assertThrows(ResponseStatusException.class,
                () -> controller.compatibles(4, "Derecho", authentication));
    }

    private Fundacion fundacion(int id) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(id);
        fundacion.setNombre("Fundación " + id);
        fundacion.setActiva(true);
        return fundacion;
    }

    private TutorFundacion vinculo(Fundacion fundacion, String... carreras) {
        Usuario tutor = new Usuario();
        tutor.setId(8);
        tutor.setNombre("Tutor");
        tutor.setApellido("Externo");
        tutor.setEmail("tutor@fundacion.ec");
        tutor.setActivo(true);
        Rol rol = new Rol();
        rol.setCodigo("TUTOR");
        tutor.setRoles(Set.of(rol));

        TutorFundacion vinculo = new TutorFundacion();
        vinculo.setId(12);
        vinculo.setFundacion(fundacion);
        vinculo.setUsuario(tutor);
        vinculo.setNombre("Tutor Externo");
        vinculo.setActivo(true);
        Set<Carrera> cobertura = new LinkedHashSet<>();
        for (String nombre : carreras) {
            Carrera carrera = new Carrera();
            carrera.setNombre(nombre);
            cobertura.add(carrera);
        }
        vinculo.setCarreras(cobertura);
        return vinculo;
    }

    private Authentication auth(String rol) {
        return new UsernamePasswordAuthenticationToken(
                "usuario", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
