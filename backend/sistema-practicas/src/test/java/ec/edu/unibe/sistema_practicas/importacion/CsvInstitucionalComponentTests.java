package ec.edu.unibe.sistema_practicas.importacion;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvInstitucionalComponentTests {

    private final CsvInstitucionalComponent component = new CsvInstitucionalComponent();

    @Test
    void lee_csv_con_bom_y_punto_coma() {
        String contenido = "\uFEFFID Externo;Correo Institucional;Nombre\n"
                + "U-001;ana@unibe.edu.ec;Ana\n";
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "estudiantes.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));

        List<FilaCsv> filas = component.leer(archivo);

        assertEquals(1, filas.size());
        assertEquals(2, filas.get(0).numero());
        assertEquals("U-001", filas.get(0).valor("id_externo"));
        assertEquals("ana@unibe.edu.ec", filas.get(0).valor("correo_institucional"));
    }

    @Test
    void ignora_filas_vacias() {
        String contenido = "external_id,email_institucional\n\nU-002,luis@unibe.edu.ec\n";
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "estudiantes.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));

        List<FilaCsv> filas = component.leer(archivo);

        assertEquals(1, filas.size());
        assertEquals("U-002", filas.get(0).valor("external_id"));
    }
}
