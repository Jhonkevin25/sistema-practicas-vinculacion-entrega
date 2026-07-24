package ec.edu.unibe.sistema_practicas.importacion;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CsvInstitucionalComponent {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_FILAS = 5000;

    public List<FilaCsv> leer(MultipartFile archivo) {
        validarArchivo(archivo);
        try {
            String contenido = new String(archivo.getBytes(), StandardCharsets.UTF_8);
            if (contenido.startsWith("\uFEFF")) {
                contenido = contenido.substring(1);
            }
            if (contenido.isBlank()) {
                throw new IllegalArgumentException("El archivo CSV está vacío.");
            }

            char separador = detectarSeparador(contenido);
            CSVFormat formato = CSVFormat.DEFAULT.builder()
                    .setDelimiter(separador)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build();

            try (CSVParser parser = new CSVParser(new StringReader(contenido), formato)) {
                if (parser.getHeaderMap().isEmpty()) {
                    throw new IllegalArgumentException("El CSV debe incluir una fila de encabezados.");
                }
                List<FilaCsv> filas = new ArrayList<>();
                for (CSVRecord record : parser) {
                    Map<String, String> valores = new LinkedHashMap<>();
                    for (String encabezado : parser.getHeaderMap().keySet()) {
                        valores.put(normalizarEncabezado(encabezado), record.get(encabezado).trim());
                    }
                    if (valores.values().stream().allMatch(String::isBlank)) {
                        continue;
                    }
                    filas.add(new FilaCsv(record.getRecordNumber() + 1, valores));
                    if (filas.size() > MAX_FILAS) {
                        throw new IllegalArgumentException("El CSV supera el máximo de " + MAX_FILAS + " filas.");
                    }
                }
                if (filas.isEmpty()) {
                    throw new IllegalArgumentException("El CSV no contiene filas de datos.");
                }
                return filas;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo CSV.");
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar un archivo CSV.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo CSV no puede superar 2 MB.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .csv.");
        }
    }

    private char detectarSeparador(String contenido) {
        String primeraLinea = contenido.lines().findFirst().orElse("");
        long comas = primeraLinea.chars().filter(c -> c == ',').count();
        long puntoComas = primeraLinea.chars().filter(c -> c == ';').count();
        return puntoComas > comas ? ';' : ',';
    }

    private String normalizarEncabezado(String encabezado) {
        String sinAcentos = Normalizer.normalize(encabezado == null ? "" : encabezado, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
