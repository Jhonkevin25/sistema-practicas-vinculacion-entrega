package ec.edu.unibe.sistema_practicas.tutorempresa;

import java.util.Set;

public record TutorEmpresaRequest(
        Integer tutorId,
        Integer empresaId,
        String cargo,
        Boolean activo,
        Set<Integer> carreraIds
) {
}
