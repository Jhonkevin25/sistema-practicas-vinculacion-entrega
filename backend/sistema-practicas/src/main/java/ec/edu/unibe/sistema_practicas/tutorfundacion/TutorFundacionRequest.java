package ec.edu.unibe.sistema_practicas.tutorfundacion;

import java.util.Set;

public record TutorFundacionRequest(
        Integer tutorId,
        Integer fundacionId,
        String cargo,
        Boolean activo,
        Set<Integer> carreraIds
) {
}
