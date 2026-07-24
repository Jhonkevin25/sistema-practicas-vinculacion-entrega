package ec.edu.unibe.sistema_practicas.seguimiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FinalizacionExcepcionalRequest(
        @NotBlank
        @Pattern(regexp = "REPROBADO|RETIRADO")
        String estado,
        @NotBlank
        @Size(min = 10)
        String motivo
) {}
