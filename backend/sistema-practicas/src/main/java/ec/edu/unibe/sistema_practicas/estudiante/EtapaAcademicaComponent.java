package ec.edu.unibe.sistema_practicas.estudiante;

import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class EtapaAcademicaComponent {

    public static final String PROCESO_VINCULACION = "VINCULACION";
    public static final String PROCESO_PRACTICAS = "PRACTICAS";

    private final VinculacionRepository vinculacionRepository;
    private final PracticaRepository practicaRepository;

    public String procesoActual(Estudiante estudiante) {
        if (estudiante == null || estudiante.getId() == null) {
            throw new IllegalArgumentException("El estudiante no tiene un expediente válido.");
        }

        boolean vinculacionCompletada = vinculacionRepository.findByEstudianteId(estudiante.getId()).stream()
                .anyMatch(vinculacion -> "completado".equalsIgnoreCase(vinculacion.getEstado()));
        if (!vinculacionCompletada) {
            return PROCESO_VINCULACION;
        }

        long practicasCompletadas = practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                .filter(practica -> "completado".equalsIgnoreCase(practica.getEstado()))
                .count();
        return practicasCompletadas < 2 ? PROCESO_PRACTICAS : null;
    }

    public void exigirProcesoDocumentalActual(Estudiante estudiante, String procesoSolicitado) {
        String procesoActual = procesoActual(estudiante);
        if (procesoActual == null) {
            throw new IllegalArgumentException(
                    "Ya completaste Vinculación, Práctica I y Práctica II; no tienes una carga documental pendiente.");
        }

        String proceso = procesoSolicitado == null
                ? ""
                : procesoSolicitado.trim().toUpperCase(Locale.ROOT);
        if (!procesoActual.equals(proceso)) {
            String etapa = PROCESO_VINCULACION.equals(procesoActual) ? "Vinculación" : "Prácticas";
            throw new IllegalArgumentException(
                    "Tu proceso académico actual es " + etapa
                            + ". Solo puedes cargar documentos en ese módulo.");
        }
    }
}
