package ec.edu.unibe.sistema_practicas.bitacora;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReenvioBitacoraRequest(
        @NotNull LocalDate fecha,
        @NotBlank String actividad,
        @NotNull @Min(1) @Max(24) Integer horas,
        String observaciones
) {}
