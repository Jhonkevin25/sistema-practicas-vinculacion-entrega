package ec.edu.unibe.sistema_practicas.usuario;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.tutorempresa.TutorEmpresa;
import ec.edu.unibe.sistema_practicas.tutorempresa.TutorEmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TutorAsignacionComponentTests {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TutorEmpresaRepository tutorEmpresaRepository;
    @InjectMocks private TutorAsignacionComponent component;

    @Test
    void acepta_tutor_activo_compatible_con_el_proceso() {
        Usuario tutor = tutor(4, "AMBOS", true, "TUTOR");
        when(usuarioRepository.findById(4)).thenReturn(Optional.of(tutor));

        assertEquals(tutor, component.exigirValido(referencia(4), "PRACTICAS"));
        assertEquals(tutor, component.exigirValido(referencia(4), "VINCULACION"));
    }

    @Test
    void rechaza_usuario_sin_rol_tutor_o_de_area_incompatible() {
        Usuario coordinador = tutor(7, "AMBOS", true, "COORDINADOR");
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(coordinador));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirValido(referencia(7), "PRACTICAS"));

        Usuario tutorVinculacion = tutor(8, "VINCULACION", true, "TUTOR");
        when(usuarioRepository.findById(8)).thenReturn(Optional.of(tutorVinculacion));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirValido(referencia(8), "PRACTICAS"));
    }

    @Test
    void acepta_tutor_externo_de_la_empresa_y_carrera_de_la_vacante() {
        Usuario tutor = tutor(9, "PRACTICAS", true, "TUTOR");
        Empresa empresa = empresa(3);
        TutorEmpresa vinculo = vinculo(tutor, empresa, true, "Ingeniería en Software", "Derecho");
        when(usuarioRepository.findById(9)).thenReturn(Optional.of(tutor));
        when(tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(9, 3)).thenReturn(Optional.of(vinculo));

        assertEquals(tutor, component.exigirValido(
                referencia(9), "PRACTICAS", empresa, "Ingenieria en Software"));
        assertEquals(tutor, component.exigirValido(
                referencia(9), "PRACTICAS", empresa, "Derecho"));
    }

    @Test
    void rechaza_tutor_de_otra_empresa_o_sin_la_carrera() {
        Usuario tutor = tutor(10, "PRACTICAS", true, "TUTOR");
        Empresa empresa = empresa(4);
        when(usuarioRepository.findById(10)).thenReturn(Optional.of(tutor));
        when(tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(10, 4)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirValido(referencia(10), "PRACTICAS", empresa, "Derecho"));

        TutorEmpresa vinculo = vinculo(tutor, empresa, true, "Administración de Empresas");
        when(tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(10, 4)).thenReturn(Optional.of(vinculo));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirValido(referencia(10), "PRACTICAS", empresa, "Derecho"));
    }

    private Usuario referencia(int id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Usuario tutor(int id, String tipo, boolean activo, String rolCodigo) {
        Usuario usuario = referencia(id);
        usuario.setActivo(activo);
        usuario.setTutorTipo(tipo);
        Rol rol = new Rol();
        rol.setCodigo(rolCodigo);
        usuario.setRoles(Set.of(rol));
        return usuario;
    }

    private Empresa empresa(int id) {
        Empresa empresa = new Empresa();
        empresa.setId(id);
        empresa.setActivo(true);
        return empresa;
    }

    private TutorEmpresa vinculo(Usuario tutor, Empresa empresa, boolean activo, String... carreras) {
        TutorEmpresa vinculo = new TutorEmpresa();
        vinculo.setUsuario(tutor);
        vinculo.setEmpresa(empresa);
        vinculo.setActivo(activo);
        Set<Carrera> cobertura = new java.util.LinkedHashSet<>();
        for (String nombre : carreras) {
            Carrera carrera = new Carrera();
            carrera.setNombre(nombre);
            cobertura.add(carrera);
        }
        vinculo.setCarreras(cobertura);
        return vinculo;
    }
}
