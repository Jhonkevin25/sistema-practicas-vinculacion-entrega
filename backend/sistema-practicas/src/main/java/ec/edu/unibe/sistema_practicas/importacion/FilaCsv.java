package ec.edu.unibe.sistema_practicas.importacion;

import java.util.Map;

public record FilaCsv(long numero, Map<String, String> valores) {

    public String valor(String columna) {
        return valores.getOrDefault(columna, "").trim();
    }
}
