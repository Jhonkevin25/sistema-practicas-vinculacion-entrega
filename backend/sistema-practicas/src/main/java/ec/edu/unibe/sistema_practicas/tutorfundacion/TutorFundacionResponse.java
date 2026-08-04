package ec.edu.unibe.sistema_practicas.tutorfundacion;

import java.util.List;

public record TutorFundacionResponse(
        Integer id,
        Integer tutorId,
        String nombre,
        String apellido,
        String email,
        Integer fundacionId,
        String fundacion,
        String cargo,
        Boolean activo,
        List<CarreraResumen> carreras
) {
    public record CarreraResumen(Integer id, String nombre) {
    }
}
