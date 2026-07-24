package ec.edu.unibe.sistema_practicas.vinculacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiberacionCupoRequest(
        @NotBlank
        @Size(min = 10)
        String motivo
) {}
