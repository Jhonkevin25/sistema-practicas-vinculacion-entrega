package ec.edu.unibe.sistema_practicas.comentarioseguimiento;

public record ComentarioSeguimientoRequest(
        Integer practicaId,
        Integer vinculacionId,
        String audiencia,
        String mensaje
) {}
