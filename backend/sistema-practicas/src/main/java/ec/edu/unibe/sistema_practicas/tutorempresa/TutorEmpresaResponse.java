package ec.edu.unibe.sistema_practicas.tutorempresa;

import java.util.List;

public record TutorEmpresaResponse(
        Integer id,
        Integer tutorId,
        String nombre,
        String apellido,
        String email,
        Integer empresaId,
        String empresa,
        String cargo,
        Boolean activo,
        List<CarreraResumen> carreras
) {
    public record CarreraResumen(Integer id, String nombre) {
    }
}
