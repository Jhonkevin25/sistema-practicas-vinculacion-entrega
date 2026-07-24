package ec.edu.unibe.sistema_practicas.comentarioseguimiento;

import java.time.LocalDateTime;

public record ComentarioSeguimientoResponse(
        Integer id,
        Integer practicaId,
        Integer vinculacionId,
        Integer autorId,
        String autor,
        String autorRol,
        String audiencia,
        String mensaje,
        LocalDateTime fechaCreacion
) {}
