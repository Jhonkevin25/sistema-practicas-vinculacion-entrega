package ec.edu.unibe.sistema_practicas.importacion;

import java.util.List;

public record ResumenImportacion(
        Long importacionId,
        String archivo,
        String tipo,
        String estado,
        int filasTotal,
        int filasOk,
        int filasError,
        int creados,
        int actualizados,
        int enlazados,
        List<ErrorFilaImportacion> errores
) {
}
