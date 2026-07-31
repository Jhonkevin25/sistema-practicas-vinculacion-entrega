package ec.edu.unibe.sistema_practicas.ofertacupo;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoria;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
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
class OfertaCuposEmpresaControllerTests {

    @Mock private OfertaCuposEmpresaRepository ofertaRepository;
    @Mock private OfertaCuposEmpresaComponent ofertaComponent;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private CarreraRepository carreraRepository;
    @Mock private FechasConvocatoriaRepository fechasConvocatoriaRepository;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private AlcanceCoordinador alcanceCoordinador;

    @InjectMocks private OfertaCuposEmpresaController controller;

    @Test
    void coordinador_ve_solo_ofertas_de_empresas_compatibles() {
        Authentication coordinador = autenticacion("COORDINADOR");
        OfertaCuposEmpresa visible = oferta(1, 10);
        OfertaCuposEmpresa oculta = oferta(2, 20);
        when(alcanceCoordinador.procesoVisible(coordinador, "PRACTICAS")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(coordinador))
                .thenReturn(Optional.of(Set.of("Derecho")));
        when(convenioVigenteComponent.empresasVisiblesPara(Set.of("Derecho")))
                .thenReturn(Set.of(10));
        when(ofertaRepository.findAll()).thenReturn(List.of(visible, oculta));
        when(ofertaComponent.enriquecerTodas(List.of(visible))).thenReturn(List.of(visible));

        assertEquals(List.of(visible), controller.getAll(null, coordinador));
    }

    @Test
    void coordinador_no_accede_por_id_a_oferta_fuera_de_alcance() {
        Authentication coordinador = autenticacion("COORDINADOR");
        OfertaCuposEmpresa oculta = oferta(2, 20);
        when(alcanceCoordinador.procesoVisible(coordinador, "PRACTICAS")).thenReturn(true);
        when(alcanceCoordinador.carrerasVisibles(coordinador))
                .thenReturn(Optional.of(Set.of("Derecho")));
        when(convenioVigenteComponent.empresasVisiblesPara(Set.of("Derecho")))
                .thenReturn(Set.of(10));
        when(ofertaRepository.findById(2)).thenReturn(Optional.of(oculta));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getById(2, coordinador));

        assertEquals(403, error.getStatusCode().value());
        verify(ofertaComponent, never()).enriquecer(oculta);
    }

    @Test
    void update_conserva_asignacion_existente_y_agrega_nueva_carrera() {
        Authentication admin = autenticacion("ADMIN");
        Empresa empresa = empresa(10);
        Carrera software = carrera(8, "Ingeniería en Software");
        Carrera derecho = carrera(2, "Derecho");
        OfertaCuposEmpresa existente = oferta(10, empresa.getId());
        existente.setEmpresa(empresa);
        existente.setPeriodoAcademico("2026-2");
        existente.setDistribucion("POR_CARRERA");
        existente.setActivo(true);
        OfertaCuposEmpresaCarrera softwareGuardado = asignacion(100, existente, software, 3);
        existente.getCarreras().add(softwareGuardado);

        OfertaCuposEmpresa cambios = new OfertaCuposEmpresa();
        cambios.setEmpresa(empresa);
        cambios.setPeriodoAcademico("2026-2");
        cambios.setDistribucion("POR_CARRERA");
        cambios.setActivo(true);
        cambios.setCarreras(List.of(
                asignacion(null, cambios, software, 3),
                asignacion(null, cambios, derecho, 2)));

        when(alcanceCoordinador.procesoVisible(admin, "PRACTICAS")).thenReturn(true);
        when(ofertaRepository.findByIdForUpdate(10)).thenReturn(Optional.of(existente));
        when(fechasConvocatoriaRepository.findByPeriodoAcademicoAndTipo("2026-2", "PRACTICAS"))
                .thenReturn(Optional.of(new FechasConvocatoria()));
        when(carreraRepository.findById(software.getId())).thenReturn(Optional.of(software));
        when(carreraRepository.findById(derecho.getId())).thenReturn(Optional.of(derecho));
        when(ofertaRepository.save(existente)).thenReturn(existente);
        when(ofertaComponent.enriquecer(existente)).thenReturn(existente);

        OfertaCuposEmpresa actualizada = controller.update(10, cambios, admin).getBody();

        assertEquals(5, actualizada.getCuposTotales());
        assertEquals(2, actualizada.getCarreras().size());
        assertEquals(100, actualizada.getCarreras().stream()
                .filter(item -> item.getCarrera().getId().equals(software.getId()))
                .findFirst().orElseThrow().getId());
    }

    private OfertaCuposEmpresa oferta(Integer id, Integer empresaId) {
        Empresa empresa = empresa(empresaId);
        OfertaCuposEmpresa oferta = new OfertaCuposEmpresa();
        oferta.setId(id);
        oferta.setEmpresa(empresa);
        return oferta;
    }

    private Empresa empresa(Integer id) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        empresa.setNombre("Empresa " + id);
        empresa.setActivo(true);
        return empresa;
    }

    private Carrera carrera(Integer id, String nombre) {
        Carrera carrera = new Carrera();
        carrera.setId(id);
        carrera.setNombre(nombre);
        carrera.setActivo(true);
        return carrera;
    }

    private OfertaCuposEmpresaCarrera asignacion(
            Integer id, OfertaCuposEmpresa oferta, Carrera carrera, int cupos) {
        OfertaCuposEmpresaCarrera asignacion = new OfertaCuposEmpresaCarrera();
        asignacion.setId(id);
        asignacion.setOferta(oferta);
        asignacion.setCarrera(carrera);
        asignacion.setCupos(cupos);
        return asignacion;
    }

    private Authentication autenticacion(String rol) {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
