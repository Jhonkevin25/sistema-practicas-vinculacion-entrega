package ec.edu.unibe.sistema_practicas.estudiante;

import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtapaAcademicaComponentTests {

    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private PracticaRepository practicaRepository;

    @InjectMocks
    private EtapaAcademicaComponent component;

    @Test
    void vinculacion_es_el_proceso_inicial() {
        Estudiante estudiante = estudiante(10);
        when(vinculacionRepository.findByEstudianteId(10)).thenReturn(List.of());

        assertEquals("VINCULACION", component.procesoActual(estudiante));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirProcesoDocumentalActual(estudiante, "PRACTICAS"));
    }

    @Test
    void practicas_se_habilita_solo_despues_de_completar_vinculacion() {
        Estudiante estudiante = estudiante(11);
        when(vinculacionRepository.findByEstudianteId(11))
                .thenReturn(List.of(vinculacion("completado")));
        when(practicaRepository.findByEstudianteId(11)).thenReturn(List.of());

        assertEquals("PRACTICAS", component.procesoActual(estudiante));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirProcesoDocumentalActual(estudiante, "VINCULACION"));
    }

    @Test
    void no_admite_mas_cargas_al_completar_todo_el_recorrido() {
        Estudiante estudiante = estudiante(12);
        when(vinculacionRepository.findByEstudianteId(12))
                .thenReturn(List.of(vinculacion("completado")));
        when(practicaRepository.findByEstudianteId(12))
                .thenReturn(List.of(practica("completado"), practica("completado")));

        assertNull(component.procesoActual(estudiante));
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirProcesoDocumentalActual(estudiante, "PRACTICAS"));
    }

    private Estudiante estudiante(Integer id) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        return estudiante;
    }

    private Vinculacion vinculacion(String estado) {
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setEstado(estado);
        return vinculacion;
    }

    private Practica practica(String estado) {
        Practica practica = new Practica();
        practica.setEstado(estado);
        return practica;
    }
}
