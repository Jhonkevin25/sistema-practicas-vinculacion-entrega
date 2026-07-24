package ec.edu.unibe.sistema_practicas.importacion;

public record FilaNotaInstitucional(
        String externalId,
        String emailInstitucional,
        String periodoAcademico,
        Integer semestre,
        Double promedio
) {
}
