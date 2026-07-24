package ec.edu.unibe.sistema_practicas.usuario;

import ec.edu.unibe.sistema_practicas.notificacion.CorreoNotificacionComponent;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTests {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CorreoNotificacionComponent correoNotificacionComponent;

    @InjectMocks
    private UsuarioController controller;

    @Test
    void create_envia_bienvenida_con_clave_temporal_despues_de_guardar() {
        Usuario nuevo = usuario(null, true, "TUTOR");
        nuevo.setPasswordHash("Pravi2026#");
        nuevo.setTutorTipo("PRACTICAS");
        when(passwordEncoder.encode("Pravi2026#")).thenReturn("hash-seguro");
        when(usuarioRepository.save(nuevo)).thenAnswer(invocacion -> {
            Usuario guardado = invocacion.getArgument(0);
            guardado.setId(20);
            return guardado;
        });

        Usuario guardado = controller.create(nuevo);

        assertEquals("hash-seguro", guardado.getPasswordHash());
        assertTrue(guardado.getPrimerLogin());
        verify(correoNotificacionComponent).enviarBienvenida(guardado, "Pravi2026#");
    }

    @Test
    void create_conserva_el_alta_si_falla_la_programacion_del_correo() {
        Usuario nuevo = usuario(null, true, "COORDINADOR");
        nuevo.setPasswordHash("Pravi2026#");
        when(passwordEncoder.encode("Pravi2026#")).thenReturn("hash-seguro");
        when(usuarioRepository.save(nuevo)).thenAnswer(invocacion -> {
            Usuario guardado = invocacion.getArgument(0);
            guardado.setId(21);
            return guardado;
        });
        doThrow(new IllegalStateException("SMTP no disponible"))
                .when(correoNotificacionComponent).enviarBienvenida(any(Usuario.class), any(String.class));

        Usuario guardado = controller.create(nuevo);

        assertEquals(21, guardado.getId());
        assertEquals("hash-seguro", guardado.getPasswordHash());
    }

    @Test
    void create_rechaza_cedula_que_supera_limite_del_esquema() {
        Usuario nuevo = usuario(null, true, "COORDINADOR");
        nuevo.setCedula("TEST-MVP47-CP-DER-005");
        nuevo.setPasswordHash("Pravi2026#");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.create(nuevo));

        assertEquals("La cédula no puede superar 20 caracteres.", error.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void getTutores_incluye_area_necesaria_para_vincularlo_con_empresa() {
        Usuario tutor = usuario(30, true, "TUTOR");
        tutor.setTutorTipo("PRACTICAS");
        when(usuarioRepository.findDistinctByRoles_CodigoAndActivoTrueOrderByApellidoAscNombreAsc("TUTOR"))
                .thenReturn(List.of(tutor));

        UsuarioController.TutorResumen resumen = controller.getTutores().get(0);

        assertEquals("PRACTICAS", resumen.tutorTipo());
        assertEquals("TUTOR", resumen.roles().get(0).codigo());
    }

    @Test
    void delete_desactiva_usuario_y_conserva_registro() {
        Usuario usuario = usuario(10, true, "COORDINADOR");
        when(usuarioRepository.findById(10)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        controller.delete(10, usuario(1, true, "ADMIN"));

        assertFalse(usuario.getActivo());
        verify(usuarioRepository).save(usuario);
        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    void delete_no_permite_desactivar_cuenta_propia() {
        Usuario administrador = usuario(1, true, "ADMIN");
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(administrador));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.delete(1, administrador));

        assertEquals("No puedes desactivar tu propia cuenta ni retirar tu rol de administrador.", error.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void delete_no_permite_desactivar_ultimo_admin_activo() {
        Usuario administrador = usuario(1, true, "ADMIN");
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.countDistinctByActivoTrueAndRoles_Codigo("ADMIN")).thenReturn(1L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.delete(1, usuario(2, true, "COORDINADOR")));

        assertEquals("Debe permanecer al menos un administrador activo en el sistema.", error.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void update_no_permite_retirar_rol_al_ultimo_admin_activo() {
        Usuario administrador = usuario(1, true, "ADMIN");
        Usuario cambios = copia(administrador);
        cambios.setRoles(Set.of(rol("COORDINADOR")));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.countDistinctByActivoTrueAndRoles_Codigo("ADMIN")).thenReturn(1L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.update(1, cambios, usuario(2, true, "ADMIN")));

        assertEquals("Debe permanecer al menos un administrador activo en el sistema.", error.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void update_permite_reactivar_usuario_inactivo() {
        Usuario usuario = usuario(10, false, "TUTOR");
        Usuario cambios = copia(usuario);
        cambios.setActivo(true);
        when(usuarioRepository.findById(10)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario actualizado = controller.update(10, cambios, usuario(1, true, "ADMIN")).getBody();

        assertTrue(actualizado.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    private Usuario copia(Usuario original) {
        Usuario copia = new Usuario();
        copia.setCedula(original.getCedula());
        copia.setNombre(original.getNombre());
        copia.setApellido(original.getApellido());
        copia.setEmail(original.getEmail());
        copia.setActivo(original.getActivo());
        copia.setPrimerLogin(original.getPrimerLogin());
        copia.setFuente(original.getFuente());
        copia.setTutorTipo(original.getTutorTipo());
        copia.setRoles(original.getRoles());
        return copia;
    }

    private Usuario usuario(Integer id, boolean activo, String codigoRol) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCedula("000000000" + id);
        usuario.setNombre("Usuario");
        usuario.setApellido(String.valueOf(id));
        usuario.setEmail("usuario" + id + "@unibe.edu.ec");
        usuario.setActivo(activo);
        usuario.setPrimerLogin(false);
        usuario.setFuente("MANUAL");
        usuario.setRoles(Set.of(rol(codigoRol)));
        return usuario;
    }

    private Rol rol(String codigo) {
        Rol rol = new Rol();
        rol.setCodigo(codigo);
        rol.setNombre(codigo);
        rol.setActivo(true);
        return rol;
    }
}
