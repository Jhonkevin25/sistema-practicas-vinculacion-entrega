package ec.edu.unibe.sistema_practicas.importacion;

public record FilaEstudianteInstitucional(
        String externalId,
        String cedula,
        String emailInstitucional,
        String nombre,
        String apellido,
        String matricula,
        String carrera,
        Integer semestre,
        String periodoAcademico,
        boolean matriculaActiva
) {
}
