package ec.edu.unibe.sistema_practicas.documento;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EtapaAcademicaComponent;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocEstudianteControllerTests {

    @Mock private DocEstudianteRepository documentoRepository;
    @Mock private DocumentoRequeridoRepository requeridoRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private SupabaseStorageComponent storageComponent;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private CierreExpedienteComponent cierreExpedienteComponent;
    @Mock private AuditoriaEmitter auditoriaEmitter;
    @Mock private EtapaAcademicaComponent etapaAcademicaComponent;

    @InjectMocks
    private DocEstudianteController controller;

    @Test
    void crear_documento_requerido_genera_auditoria() {
        Usuario admin = new Usuario();
        admin.setId(1);
        DocumentoRequerido requerido = new DocumentoRequerido();
        requerido.setProceso("PRACTICAS");
        requerido.setTipoDocumento("informe_final");
        requerido.setNombre("Informe final");
        requerido.setMomento("CIERRE");
        requerido.setObligatorio(true);
        when(requeridoRepository.save(any(DocumentoRequerido.class))).thenAnswer(inv -> {
            DocumentoRequerido guardado = inv.getArgument(0);
            guardado.setId(40);
            return guardado;
        });

        controller.createRequerido(requerido, admin, autenticacion(admin, "ADMIN"));

        verify(auditoriaEmitter).registrar(eq("DOCUMENTOS_REQUERIDOS"), eq("CREAR_REQUISITO"),
                eq(admin), eq(null), anyMap());
    }

    @Test
    void coordinador_no_gestiona_requisito_de_otro_proceso() {
        Usuario coordinador = new Usuario();
        coordinador.setId(2);
        DocumentoRequerido requerido = new DocumentoRequerido();
        requerido.setProceso("VINCULACION");
        requerido.setTipoDocumento("informe_final");
        requerido.setNombre("Informe final");
        requerido.setMomento("CIERRE");
        requerido.setObligatorio(true);
        Authentication authentication = autenticacion(coordinador, "COORDINADOR");
        when(alcanceCoordinador.procesoVisible(authentication, "VINCULACION")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> controller.createRequerido(requerido, coordinador, authentication));
    }

    @Test
    void estudiante_no_crea_documento_para_un_proceso_fuera_de_su_etapa() {
        Usuario estudianteUsuario = usuarioEstudiante();
        Estudiante estudiante = estudiante(estudianteUsuario);
        when(estudianteRepository.findByUsuarioEmail(estudianteUsuario.getEmail()))
                .thenReturn(Optional.of(estudiante));
        doThrow(new IllegalArgumentException("Proceso académico incorrecto."))
                .when(etapaAcademicaComponent)
                .exigirProcesoDocumentalActual(estudiante, "PRACTICAS");

        assertThrows(IllegalArgumentException.class,
                () -> controller.subir("cv", "PRACTICAS", estudianteUsuario,
                        autenticacion(estudianteUsuario, "ESTUDIANTE")));

        verify(documentoRepository, never()).save(any(DocEstudiante.class));
    }

    @Test
    void estudiante_no_sube_a_storage_un_documento_de_otro_proceso() throws Exception {
        Usuario estudianteUsuario = usuarioEstudiante();
        Estudiante estudiante = estudiante(estudianteUsuario);
        DocEstudiante documento = new DocEstudiante();
        documento.setId(50);
        documento.setEstudiante(estudiante);
        documento.setProceso("PRACTICAS");
        when(estudianteRepository.findByUsuarioEmail(estudianteUsuario.getEmail()))
                .thenReturn(Optional.of(estudiante));
        when(documentoRepository.findById(50)).thenReturn(Optional.of(documento));
        doThrow(new IllegalArgumentException("Proceso académico incorrecto."))
                .when(etapaAcademicaComponent)
                .exigirProcesoDocumentalActual(estudiante, "PRACTICAS");
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "cv.pdf", "application/pdf", "contenido".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> controller.subirArchivo(50, archivo, estudianteUsuario,
                        autenticacion(estudianteUsuario, "ESTUDIANTE")));

        verify(storageComponent, never()).subir(any(), any());
    }

    private Usuario usuarioEstudiante() {
        Usuario usuario = new Usuario();
        usuario.setId(20);
        usuario.setEmail("estudiante@unibe.edu.ec");
        return usuario;
    }

    private Estudiante estudiante(Usuario usuario) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(30);
        estudiante.setUsuario(usuario);
        return estudiante;
    }

    private Authentication autenticacion(Usuario usuario, String rol) {
        return new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
}
