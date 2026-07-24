package ec.edu.unibe.sistema_practicas.seguimiento;

import java.time.LocalDateTime;

public record EventoLineaTiempo(
        String clave,
        String tipo,
        String titulo,
        String descripcion,
        LocalDateTime fecha,
        String estado,
        String actor
) {}
