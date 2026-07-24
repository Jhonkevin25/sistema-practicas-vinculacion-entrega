package ec.edu.unibe.sistema_practicas.proyecto;

import ec.edu.unibe.sistema_practicas.ofertacupofundacion.OfertaCuposFundacionComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CuposProyectoComponent {

    private final ProyectoRepository proyectoRepository;

    public void exigirDisponible(Proyecto proyecto, String carrera) {
        ProyectoCarrera participacion = exigirCarreraParticipante(proyecto, carrera);
        if (!Boolean.TRUE.equals(proyecto.getEstado())) {
            throw new IllegalArgumentException("El proyecto seleccionado no está activo.");
        }
        if (valor(proyecto.getCuposDisponibles()) <= 0) {
            throw new IllegalArgumentException("El proyecto seleccionado no tiene cupos disponibles.");
        }
        if (participacion.getCuposDisponibles() != null
                && participacion.getCuposDisponibles() <= 0) {
            throw new IllegalArgumentException(
                    "El proyecto no tiene cupos disponibles para la carrera del estudiante.");
        }
    }

    public void descontar(Proyecto proyecto, String carrera) {
        ProyectoCarrera participacion = exigirCarreraParticipante(proyecto, carrera);
        exigirDisponible(proyecto, carrera);
        proyecto.setCuposDisponibles(proyecto.getCuposDisponibles() - 1);
        if (participacion.getCuposDisponibles() != null) {
            participacion.setCuposDisponibles(participacion.getCuposDisponibles() - 1);
        }
        proyectoRepository.save(proyecto);
    }

    // Fase 42: devuelve al proyecto el cupo de una vinculación retirada,
    // sin superar nunca los cupos totales autorizados.
    public void reintegrar(Proyecto proyecto, String carrera) {
        ProyectoCarrera participacion = exigirCarreraParticipante(proyecto, carrera);
        int topeTotal = proyecto.getCuposTotales() == null
                ? Integer.MAX_VALUE
                : proyecto.getCuposTotales();
        proyecto.setCuposDisponibles(
                Math.min(topeTotal, valor(proyecto.getCuposDisponibles()) + 1));
        if (participacion.getCuposDisponibles() != null) {
            int topeCarrera = participacion.getCuposTotales() == null
                    ? Integer.MAX_VALUE
                    : participacion.getCuposTotales();
            participacion.setCuposDisponibles(
                    Math.min(topeCarrera, participacion.getCuposDisponibles() + 1));
        }
        proyectoRepository.save(proyecto);
    }

    public ProyectoCarrera exigirCarreraParticipante(Proyecto proyecto, String carrera) {
        if (proyecto == null || proyecto.getCarreras() == null) {
            throw new IllegalArgumentException("El proyecto no tiene carreras participantes configuradas.");
        }
        return proyecto.getCarreras().stream()
                .filter(item -> item.getCarrera() != null
                        && OfertaCuposFundacionComponent.mismaCarrera(
                                item.getCarrera().getNombre(), carrera))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El proyecto no está habilitado para la carrera del estudiante."));
    }

    private int valor(Integer numero) {
        return numero == null ? 0 : numero;
    }
}
