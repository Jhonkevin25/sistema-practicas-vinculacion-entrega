package ec.edu.unibe.sistema_practicas.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String nombre;
    private String apellido;
    private String email;
    private String rol;
    private String carrera;
    private Boolean primerLogin;
    // Solo para rol TUTOR: PRACTICAS | VINCULACION | AMBOS
    private String tutorTipo;
}
