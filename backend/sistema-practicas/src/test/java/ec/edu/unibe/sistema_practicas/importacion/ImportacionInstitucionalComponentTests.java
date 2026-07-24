package ec.edu.unibe.sistema_practicas.importacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademica;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademicaRepository;
import ec.edu.unibe.sistema_practicas.notificacion.CorreoNotificacionComponent;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.rol.RolRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacionInstitucionalComponentTests {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private RolRepository rolRepository;
    @Mock private NotaAcademicaRepository notaAcademicaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CorreoNotificacionComponent correoNotificacionComponent;

    @InjectMocks
    private ImportacionInstitucionalComponent component;

    @Test
    void reimportar_estudiante_actualiza_sin_duplicar() {
        Rol rol = rolEstudiante();
        AtomicReference<Usuario> usuarioGuardado = new AtomicReference<>();
        AtomicReference<Estudiante> estudianteGuardado = new AtomicReference<>();
        when(rolRepository.findByCodigo("ESTUDIANTE")).thenReturn(Optional.of(rol));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepository.findByExternalIdIgnoreCase("U-001"))
                .thenAnswer(inv -> Optional.ofNullable(usuarioGuardado.get()));
        when(usuarioRepository.findByEmailIgnoreCase("ana@unibe.edu.ec"))
                .thenAnswer(inv -> Optional.ofNullable(usuarioGuardado.get()));
        when(usuarioRepository.findByCedula("1711111111")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            if (usuario.getId() == null) usuario.setId(10);
            usuarioGuardado.set(usuario);
            return usuario;
        });
        when(estudianteRepository.findByUsuarioId(10))
                .thenAnswer(inv -> Optional.ofNullable(estudianteGuardado.get()));
        when(estudianteRepository.findByMatricula("MAT-001"))
                .thenAnswer(inv -> Optional.ofNullable(estudianteGuardado.get()));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(inv -> {
            Estudiante estudiante = inv.getArgument(0);
            if (estudiante.getId() == null) estudiante.setId(20);
            estudianteGuardado.set(estudiante);
            return estudiante;
        });

        FilaEstudianteInstitucional fila = new FilaEstudianteInstitucional(
                "U-001", "1711111111", "ana@unibe.edu.ec", "Ana", "Pérez",
                "MAT-001", "Ingeniería en Software", 5, "2026-1", true);

        assertEquals(AccionImportacion.CREADO, component.importarEstudiante(fila));
        assertEquals(AccionImportacion.ACTUALIZADO, component.importarEstudiante(fila));
        assertEquals(AccionImportacion.ACTUALIZADO,
                component.importarEstudiante(fila, FuenteInstitucional.API_UNIVERSIDAD));
        assertEquals(10, usuarioGuardado.get().getId());
        assertEquals(20, estudianteGuardado.get().getId());
        assertEquals("API_UNIVERSIDAD", usuarioGuardado.get().getFuente());
        verify(correoNotificacionComponent, times(1)).enviarBienvenida(any(), any());
    }

    @Test
    void usuario_manual_se_enlaza_por_correo() {
        Rol rol = rolEstudiante();
        Usuario usuario = new Usuario();
        usuario.setId(11);
        usuario.setEmail("manual@unibe.edu.ec");
        usuario.setFuente("MANUAL");
        usuario.setRoles(new HashSet<>());
        Estudiante estudiante = new Estudiante();
        estudiante.setId(21);
        estudiante.setUsuario(usuario);
        estudiante.setMatricula("MAT-002");

        when(usuarioRepository.findByExternalIdIgnoreCase("U-002")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmailIgnoreCase("manual@unibe.edu.ec")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCedula("1722222222")).thenReturn(Optional.of(usuario));
        when(rolRepository.findByCodigo("ESTUDIANTE")).thenReturn(Optional.of(rol));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(estudianteRepository.findByUsuarioId(11)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.findByMatricula("MAT-002")).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(inv -> inv.getArgument(0));

        AccionImportacion accion = component.importarEstudiante(new FilaEstudianteInstitucional(
                "U-002", "1722222222", "manual@unibe.edu.ec", "Luis", "Mora",
                "MAT-002", "Derecho", 4, "2026-1", true));

        assertEquals(AccionImportacion.ENLAZADO, accion);
        assertEquals("U-002", usuario.getExternalId());
        assertEquals("CSV_UNIVERSIDAD", usuario.getFuente());
        verify(correoNotificacionComponent, never()).enviarBienvenida(any(), any());
    }

    @Test
    void reimportar_nota_actualiza_el_mismo_registro() {
        Usuario usuario = new Usuario();
        usuario.setId(12);
        usuario.setExternalId("U-003");
        usuario.setEmail("nota@unibe.edu.ec");
        Estudiante estudiante = new Estudiante();
        estudiante.setId(22);
        estudiante.setUsuario(usuario);
        estudiante.setCarrera("Ingeniería en Software");
        estudiante.setSemestre(5);
        Usuario admin = new Usuario();
        admin.setId(1);
        AtomicReference<NotaAcademica> notaGuardada = new AtomicReference<>();

        when(usuarioRepository.findByExternalIdIgnoreCase("U-003")).thenReturn(Optional.of(usuario));
        when(estudianteRepository.findByUsuarioId(12)).thenReturn(Optional.of(estudiante));
        when(notaAcademicaRepository.findByEstudianteIdAndPeriodoAcademicoAndSemestre(22, "2025-2", 4))
                .thenAnswer(inv -> Optional.ofNullable(notaGuardada.get()));
        when(notaAcademicaRepository.save(any(NotaAcademica.class))).thenAnswer(inv -> {
            NotaAcademica nota = inv.getArgument(0);
            if (nota.getId() == null) nota.setId(30);
            notaGuardada.set(nota);
            return nota;
        });

        FilaNotaInstitucional fila = new FilaNotaInstitucional("U-003", null, "2025-2", 4, 9.25);
        assertEquals(AccionImportacion.CREADO, component.importarNota(fila, admin));
        assertEquals(AccionImportacion.ACTUALIZADO, component.importarNota(fila, admin));
        assertEquals(AccionImportacion.ACTUALIZADO,
                component.importarNota(fila, admin, FuenteInstitucional.API_UNIVERSIDAD));
        assertNotNull(notaGuardada.get());
        assertEquals("VERIFICADO", notaGuardada.get().getEstado());
        assertEquals("API_UNIVERSIDAD", notaGuardada.get().getFuente());
    }

    private Rol rolEstudiante() {
        Rol rol = new Rol();
        rol.setId(4);
        rol.setCodigo("ESTUDIANTE");
        rol.setNombre("Estudiante");
        rol.setActivo(true);
        return rol;
    }
}
