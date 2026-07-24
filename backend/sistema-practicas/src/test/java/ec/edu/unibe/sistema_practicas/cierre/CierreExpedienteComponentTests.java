package ec.edu.unibe.sistema_practicas.cierre;

import ec.edu.unibe.sistema_practicas.auditoria.Auditoria;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaRepository;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequerido;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequeridoRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreExpedienteComponentTests {

    @Mock private DocumentoRequeridoRepository requeridoRepository;
    @Mock private DocEstudianteRepository documentoRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private AuditoriaRepository auditoriaRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private CierreExpedienteComponent component;

    @Test
    void documento_obligatorio_de_la_carrera_bloquea_si_falta() {
        Estudiante estudiante = estudiante("Software");
        DocumentoRequerido requerido = requerido("Software");
        when(requeridoRepository.findByActivoTrue()).thenReturn(List.of(requerido));
        when(documentoRepository.findByEstudianteIdAndTipoDocumentoAndProceso(7, "informe_final", "PRACTICAS"))
                .thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> component.validarDocumentosCierre(estudiante, "PRACTICAS", null));

        assertEquals("No se puede cerrar: falta documento de cierre 'Informe final'.", error.getMessage());
    }

    @Test
    void documento_de_otra_carrera_no_bloquea() {
        Estudiante estudiante = estudiante("Derecho");
        when(requeridoRepository.findByActivoTrue()).thenReturn(List.of(requerido("Software")));

        assertDoesNotThrow(() -> component.validarDocumentosCierre(estudiante, "PRACTICAS", null));
        verify(documentoRepository, never()).findByEstudianteIdAndTipoDocumentoAndProceso(any(), any(), any());
    }

    @Test
    void documento_cargado_y_aprobado_habilita_el_cierre() {
        Estudiante estudiante = estudiante("Software");
        DocumentoRequerido requerido = requerido("Software");
        DocEstudiante documento = new DocEstudiante();
        documento.setUrlArchivo("estudiantes/7/informe.pdf");
        documento.setEstado("aprobado");
        when(requeridoRepository.findByActivoTrue()).thenReturn(List.of(requerido));
        when(documentoRepository.findByEstudianteIdAndTipoDocumentoAndProceso(7, "informe_final", "PRACTICAS"))
                .thenReturn(Optional.of(documento));

        assertDoesNotThrow(() -> component.validarDocumentosCierre(estudiante, "PRACTICAS", null));
    }

    @Test
    void cierre_exitoso_guarda_snapshot_y_auditoria() {
        Practica practica = new Practica();
        practica.setId(15);
        Usuario usuario = new Usuario();
        usuario.setId(3);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"estado\":\"completado\"}");
        when(auditoriaRepository.save(any(Auditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        component.registrarCierrePractica(practica, usuario, Map.of("estado", "completado"));

        assertEquals(usuario, practica.getCerradoPor());
        assertNotNull(practica.getCerradoEn());
        assertEquals("{\"estado\":\"completado\"}", practica.getCierreSnapshot());
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("CIERRE_FORMAL", captor.getValue().getAccion());
        assertEquals("PRACTICAS", captor.getValue().getTablaAfectada());
    }

    private Estudiante estudiante(String carrera) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7);
        estudiante.setCarrera(carrera);
        return estudiante;
    }

    private DocumentoRequerido requerido(String carrera) {
        DocumentoRequerido requerido = new DocumentoRequerido();
        requerido.setActivo(true);
        requerido.setObligatorio(true);
        requerido.setMomento("CIERRE");
        requerido.setProceso("PRACTICAS");
        requerido.setCarrera(carrera);
        requerido.setTipoDocumento("informe_final");
        requerido.setNombre("Informe final");
        return requerido;
    }
}
