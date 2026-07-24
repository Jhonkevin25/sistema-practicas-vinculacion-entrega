package ec.edu.unibe.sistema_practicas.importacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacionControllerTests {

    @Mock private CsvInstitucionalComponent csvInstitucionalComponent;
    @Mock private ImportacionInstitucionalComponent importacionInstitucionalComponent;
    @Mock private ImportacionRepository importacionRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private AuditoriaEmitter auditoriaEmitter;

    @InjectMocks
    private ImportacionController controller;

    @Test
    void importacion_completada_es_auditada_con_resumen() {
        Usuario admin = new Usuario();
        admin.setId(1);
        FilaCsv fila = new FilaCsv(2, Map.of(
                "external_id", "U-100",
                "cedula", "1712345678",
                "email_institucional", "fase22@unibe.edu.ec",
                "nombre", "Prueba",
                "apellido", "Fase",
                "matricula", "MAT-100",
                "carrera", "Software",
                "semestre", "5",
                "periodo_academico", "2026-1",
                "estado_matricula", "ACTIVO"));
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "estudiantes.csv", "text/csv", "datos".getBytes(StandardCharsets.UTF_8));
        when(csvInstitucionalComponent.leer(archivo)).thenReturn(List.of(fila));
        when(importacionInstitucionalComponent.importarEstudiante(any())).thenReturn(AccionImportacion.ACTUALIZADO);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(importacionRepository.save(any(Importacion.class))).thenAnswer(inv -> {
            Importacion importacion = inv.getArgument(0);
            importacion.setId(50L);
            return importacion;
        });

        ResumenImportacion resumen = controller.importarEstudiantes(archivo, admin);

        assertEquals("COMPLETADA", resumen.estado());
        assertEquals(1, resumen.actualizados());
        verify(auditoriaEmitter).registrar(eq("IMPORTACIONES"), eq("IMPORTACION_INSTITUCIONAL"),
                eq(admin), eq(null), anyMap());
    }
}
