package ec.edu.unibe.sistema_practicas.auth;

import lombok.Data;

@Data
public class CambiarPasswordRequest {
    private String passwordActual;
    private String nuevaPassword;
}
