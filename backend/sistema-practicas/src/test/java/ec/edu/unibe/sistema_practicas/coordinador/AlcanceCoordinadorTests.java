package ec.edu.unibe.sistema_practicas.coordinador;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlcanceCoordinadorTests {

    @Mock
    private CoordinadorCarreraRepository coordinadorRepository;

    @InjectMocks
    private AlcanceCoordinador alcanceCoordinador;

    @Test
    void coordinador_sin_alcance_configurado_no_hereda_acceso_global() {
        Usuario coordinador = new Usuario();
        coordinador.setId(7);
        when(coordinadorRepository.findByUsuarioId(7)).thenReturn(List.of());

        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(
                autenticacion(coordinador, "COORDINADOR"));

        assertTrue(carreras.isPresent());
        assertTrue(carreras.orElseThrow().isEmpty());
    }

    @Test
    void admin_no_se_restringe_con_el_alcance_de_coordinador() {
        Usuario admin = new Usuario();
        admin.setId(1);

        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(
                autenticacion(admin, "ADMIN"));

        assertEquals(Optional.empty(), carreras);
    }

    @Test
    void coordinador_con_principal_inesperado_tambien_falla_cerrado() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                User.withUsername("coordinador").password("x").roles("COORDINADOR").build(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));

        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(authentication);

        assertTrue(carreras.isPresent());
        assertTrue(carreras.orElseThrow().isEmpty());
    }

    @Test
    void coordinador_solo_ve_el_proceso_configurado() {
        Usuario coordinador = new Usuario();
        coordinador.setId(7);
        CoordinadorCarrera fila = alcance(coordinador, "Software", "PRACTICAS");
        when(coordinadorRepository.findByUsuarioId(7)).thenReturn(List.of(fila));
        Authentication authentication = autenticacion(coordinador, "COORDINADOR");

        assertTrue(alcanceCoordinador.procesoVisible(authentication, "PRACTICAS"));
        assertFalse(alcanceCoordinador.procesoVisible(authentication, "VINCULACION"));
    }

    @Test
    void tipos_inconsistentes_fallan_cerrados() {
        Usuario coordinador = new Usuario();
        coordinador.setId(7);
        when(coordinadorRepository.findByUsuarioId(7)).thenReturn(List.of(
                alcance(coordinador, "Software", "PRACTICAS"),
                alcance(coordinador, "Derecho", "VINCULACION")));

        assertFalse(alcanceCoordinador.procesoVisible(
                autenticacion(coordinador, "COORDINADOR"), "PRACTICAS"));
    }

    private CoordinadorCarrera alcance(Usuario usuario, String carrera, String tipo) {
        CoordinadorCarrera alcance = new CoordinadorCarrera();
        alcance.setUsuario(usuario);
        alcance.setCarrera(carrera);
        alcance.setCoordinacionTipo(tipo);
        return alcance;
    }

    private Authentication autenticacion(Usuario usuario, String rol) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
