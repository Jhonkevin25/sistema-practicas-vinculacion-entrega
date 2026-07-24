package ec.edu.unibe.sistema_practicas.proyecto;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CuposProyectoComponentTests {

    @Mock private ProyectoRepository proyectoRepository;

    @InjectMocks
    private CuposProyectoComponent component;

    @Test
    void descontar_actualiza_total_y_subcupo_sin_permitir_negativos() {
        Proyecto proyecto = proyectoConCupo("Derecho", 2, 1);

        component.descontar(proyecto, "Derecho");

        assertEquals(1, proyecto.getCuposDisponibles());
        assertEquals(0, proyecto.getCarreras().get(0).getCuposDisponibles());
        assertThrows(IllegalArgumentException.class,
                () -> component.descontar(proyecto, "Derecho"));
        verify(proyectoRepository, times(1)).save(proyecto);
    }

    @Test
    void descontar_rechaza_carrera_que_no_participa() {
        Proyecto proyecto = proyectoConCupo("Derecho", 2, 2);

        assertThrows(IllegalArgumentException.class,
                () -> component.descontar(proyecto, "Medicina"));

        verify(proyectoRepository, never()).save(proyecto);
    }

    // Fase 42: liberar el cupo de un retiro lo devuelve al proyecto sin
    // superar nunca los cupos totales autorizados.
    @Test
    void reintegrar_devuelve_cupo_sin_superar_los_totales() {
        // Proyecto con 2 cupos totales (1 para Derecho), todo consumido
        Proyecto proyecto = proyectoConCupo("Derecho", 0, 1);
        proyecto.setCuposTotales(2);
        proyecto.getCarreras().get(0).setCuposDisponibles(0);

        component.reintegrar(proyecto, "Derecho");

        assertEquals(1, proyecto.getCuposDisponibles());
        assertEquals(1, proyecto.getCarreras().get(0).getCuposDisponibles());

        component.reintegrar(proyecto, "Derecho");
        component.reintegrar(proyecto, "Derecho");

        // Tope global 2 y tope de la carrera 1 (sus cupos totales por carrera)
        assertEquals(2, proyecto.getCuposDisponibles());
        assertEquals(1, proyecto.getCarreras().get(0).getCuposDisponibles());
        verify(proyectoRepository, times(3)).save(proyecto);
    }

    @Test
    void reintegrar_rechaza_carrera_que_no_participa() {
        Proyecto proyecto = proyectoConCupo("Derecho", 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> component.reintegrar(proyecto, "Medicina"));

        verify(proyectoRepository, never()).save(proyecto);
    }

    private Proyecto proyectoConCupo(String nombreCarrera, int totalDisponible, int carreraDisponible) {
        Carrera carrera = new Carrera();
        carrera.setId(1);
        carrera.setNombre(nombreCarrera);
        Proyecto proyecto = new Proyecto();
        proyecto.setEstado(true);
        proyecto.setCuposDisponibles(totalDisponible);
        ProyectoCarrera participacion = new ProyectoCarrera();
        participacion.setProyecto(proyecto);
        participacion.setCarrera(carrera);
        participacion.setCuposTotales(carreraDisponible);
        participacion.setCuposDisponibles(carreraDisponible);
        proyecto.getCarreras().add(participacion);
        return proyecto;
    }
}
