package ec.edu.unibe.sistema_practicas.seguimiento;

import java.time.LocalDateTime;
import java.util.List;

public record LineaTiempoExpedienteResponse(
        String proceso,
        Integer expedienteId,
        String periodoAcademico,
        String estado,
        String motivoFinalizacion,
        LocalDateTime finalizadoEn,
        List<EventoLineaTiempo> eventos
) {}
