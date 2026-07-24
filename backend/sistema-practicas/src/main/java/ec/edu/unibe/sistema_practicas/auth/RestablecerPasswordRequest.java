package ec.edu.unibe.sistema_practicas.auth;

import lombok.Data;

@Data
public class RestablecerPasswordRequest {
    private String token;
    private String nuevaPassword;
}
