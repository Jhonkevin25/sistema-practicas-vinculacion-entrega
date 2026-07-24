package ec.edu.unibe.sistema_practicas.practica;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PracticaSpecification {

    public static Specification<Practica> build(
            String texto,
            String estado,
            String periodoAcademico,
            Integer estudianteId,
            Integer tutorId,
            Collection<String> carrerasGestion,
            boolean restringirProcesoGestion) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filtros de alcance (seguridad/rol). El filtro de tutor se
            // combina (AND) con el alcance por carrera para que un coordinador
            // no pueda salirse de su alcance al filtrar por tutor.
            if (estudianteId != null) {
                predicates.add(cb.equal(root.get("estudiante").get("id"), estudianteId));
            } else {
                if (tutorId != null) {
                    predicates.add(cb.equal(root.get("tutor").get("id"), tutorId));
                }
                if (restringirProcesoGestion) {
                    // Proceso fuera del alcance del coordinador: sin filas (1=0),
                    // igual que los endpoints no paginados.
                    predicates.add(cb.disjunction());
                } else if (carrerasGestion != null) {
                    if (!carrerasGestion.isEmpty()) {
                        predicates.add(root.get("estudiante").get("carrera").in(carrerasGestion));
                    } else {
                        predicates.add(cb.disjunction());
                    }
                }
            }

            // 2. Filtros de búsqueda (texto)
            if (texto != null && !texto.isBlank()) {
                String likeTexto = "%" + texto.trim().toLowerCase() + "%";
                Predicate nombrePred = cb.like(cb.lower(root.get("estudiante").get("usuario").get("nombre")), likeTexto);
                Predicate apellidoPred = cb.like(cb.lower(root.get("estudiante").get("usuario").get("apellido")), likeTexto);
                Predicate matriculaPred = cb.like(cb.lower(root.get("estudiante").get("matricula")), likeTexto);
                predicates.add(cb.or(nombrePred, apellidoPred, matriculaPred));
            }

            // 3. Filtro de estado
            if (estado != null && !estado.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("estado")), estado.trim().toLowerCase()));
            }

            // 4. Filtro de periodo
            if (periodoAcademico != null && !periodoAcademico.isBlank()) {
                predicates.add(cb.equal(root.get("periodoAcademico"), periodoAcademico.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
