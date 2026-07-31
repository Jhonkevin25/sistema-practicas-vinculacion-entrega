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
    public static final String ETAPA_PRACTICA_1 = "PRACTICA_1";
    public static final String ETAPA_PRACTICA_2 = "PRACTICA_2";

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

        return practicasCompletadas(estudiante) < 2 ? PROCESO_PRACTICAS : null;
    }

    // Etapa concreta dentro del proceso de Practicas (Practica I o Practica II),
    // usada para etiquetar documentos que no deben heredarse automaticamente de
    // una practica a la siguiente (p.ej. carta de solicitud, carta de aceptacion).
    public String etapaPracticaActual(Estudiante estudiante) {
        if (estudiante == null || estudiante.getId() == null) {
            throw new IllegalArgumentException("El estudiante no tiene un expediente válido.");
        }

        long practicasCompletadas = practicasCompletadas(estudiante);
        if (practicasCompletadas == 0) {
            return ETAPA_PRACTICA_1;
        }
        if (practicasCompletadas == 1) {
            return ETAPA_PRACTICA_2;
        }
        return null;
    }

    private long practicasCompletadas(Estudiante estudiante) {
        return practicaRepository.findByEstudianteId(estudiante.getId()).stream()
                .filter(practica -> "completado".equalsIgnoreCase(practica.getEstado()))
                .count();
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
