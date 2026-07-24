package ec.edu.unibe.sistema_practicas.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;
}