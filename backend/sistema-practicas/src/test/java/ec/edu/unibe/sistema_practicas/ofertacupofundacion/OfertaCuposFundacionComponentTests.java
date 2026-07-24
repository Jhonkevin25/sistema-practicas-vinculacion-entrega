package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoCarrera;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfertaCuposFundacionComponentTests {

    @Mock private OfertaCuposFundacionRepository ofertaRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private VinculacionRepository vinculacionRepository;

    @InjectMocks
    private OfertaCuposFundacionComponent component;

    @Test
    void oferta_general_no_permite_reservar_mas_que_la_capacidad_libre() {
        OfertaCuposFundacion oferta = oferta("GENERAL", 5);
        Proyecto proyecto = proyecto(10, 2, null, null);
        when(ofertaRepository.findByFundacionIdAndPeriodoAcademicoForUpdate(1, "2026-2"))
                .thenReturn(Optional.of(oferta));
        when(proyectoRepository.findByFundacionIdAndPeriodo(1, "2026-2"))
                .thenReturn(List.of(proyecto));
        when(vinculacionRepository.findByFundacionIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of(vinculacion("Derecho"), vinculacion("Derecho")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> component.exigirDisponibilidad(1, "2026-2", 2, Map.of(), null));

        assertEquals("La fundación solo tiene 1 cupo(s) disponibles para este periodo.",
                error.getMessage());
    }

    @Test
    void oferta_por_carrera_no_comparte_cupos_de_otra_carrera() {
        OfertaCuposFundacion oferta = oferta("POR_CARRERA", 5);
        oferta.getCarreras().add(detalle(oferta, "Derecho", 2));
        oferta.getCarreras().add(detalle(oferta, "Medicina", 3));
        Proyecto proyecto = proyecto(10, 1, "Derecho", 1);
        when(ofertaRepository.findByFundacionIdAndPeriodoAcademicoForUpdate(1, "2026-2"))
                .thenReturn(Optional.of(oferta));
        when(proyectoRepository.findByFundacionIdAndPeriodo(1, "2026-2"))
                .thenReturn(List.of(proyecto));
        when(vinculacionRepository.findByFundacionIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of(vinculacion("Derecho")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> component.exigirDisponibilidad(
                        1, "2026-2", 1, Map.of("Derecho", 1), null));

        assertEquals("La fundación solo tiene 0 cupo(s) disponibles para Derecho en este periodo.",
                error.getMessage());
    }

    private OfertaCuposFundacion oferta(String distribucion, int cupos) {
        Fundacion fundacion = new Fundacion();
        fundacion.setId(1);
        OfertaCuposFundacion oferta = new OfertaCuposFundacion();
        oferta.setFundacion(fundacion);
        oferta.setPeriodoAcademico("2026-2");
        oferta.setDistribucion(distribucion);
        oferta.setCuposTotales(cupos);
        oferta.setActivo(true);
        return oferta;
    }

    private OfertaCuposFundacionCarrera detalle(
            OfertaCuposFundacion oferta, String nombreCarrera, int cupos) {
        Carrera carrera = new Carrera();
        carrera.setNombre(nombreCarrera);
        OfertaCuposFundacionCarrera detalle = new OfertaCuposFundacionCarrera();
        detalle.setOferta(oferta);
        detalle.setCarrera(carrera);
        detalle.setCupos(cupos);
        return detalle;
    }

    private Proyecto proyecto(
            Integer id, int disponibles, String nombreCarrera, Integer disponiblesCarrera) {
        Proyecto proyecto = new Proyecto();
        proyecto.setId(id);
        proyecto.setCuposDisponibles(disponibles);
        if (nombreCarrera != null) {
            Carrera carrera = new Carrera();
            carrera.setNombre(nombreCarrera);
            ProyectoCarrera detalle = new ProyectoCarrera();
            detalle.setProyecto(proyecto);
            detalle.setCarrera(carrera);
            detalle.setCuposDisponibles(disponiblesCarrera);
            proyecto.getCarreras().add(detalle);
        }
        return proyecto;
    }

    private Vinculacion vinculacion(String carrera) {
        Estudiante estudiante = new Estudiante();
        estudiante.setCarrera(carrera);
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setEstudiante(estudiante);
        return vinculacion;
    }
}
