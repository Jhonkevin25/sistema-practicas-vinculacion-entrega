package ec.edu.unibe.sistema_practicas.ofertacupo;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfertaCuposEmpresaComponentTests {

    @Mock private OfertaCuposEmpresaRepository ofertaRepository;
    @Mock private VacantePracticaRepository vacanteRepository;
    @Mock private PracticaRepository practicaRepository;

    @InjectMocks
    private OfertaCuposEmpresaComponent component;

    @Test
    void oferta_general_resta_reservados_y_ocupados_sin_guardar_otro_contador() {
        OfertaCuposEmpresa oferta = ofertaGeneral(5);
        when(ofertaRepository.findByEmpresaIdAndPeriodoAcademicoForUpdate(1, "2026-2"))
                .thenReturn(Optional.of(oferta));
        when(vacanteRepository.findByEmpresaIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of(vacante("Ingeniería en Software", 2)));
        when(practicaRepository.findByEmpresaIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of(practica("Ingeniería en Software")));

        component.exigirDisponibilidad(1, "2026-2", "Ingenieria en Software", 2);

        assertEquals(2, oferta.getCuposReservados());
        assertEquals(1, oferta.getCuposOcupados());
        assertEquals(2, oferta.getCuposDisponibles());
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirDisponibilidad(1, "2026-2", "Ingeniería en Software", 3));
    }

    @Test
    void oferta_por_carrera_no_presta_cupos_de_otra_carrera() {
        OfertaCuposEmpresa oferta = ofertaGeneral(2);
        oferta.setDistribucion("POR_CARRERA");
        OfertaCuposEmpresaCarrera software = new OfertaCuposEmpresaCarrera();
        software.setOferta(oferta);
        software.setCarrera(carrera("Ingeniería en Software"));
        software.setCupos(2);
        oferta.setCarreras(List.of(software));
        when(ofertaRepository.findByEmpresaIdAndPeriodoAcademicoForUpdate(1, "2026-2"))
                .thenReturn(Optional.of(oferta));
        when(vacanteRepository.findByEmpresaIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of());
        when(practicaRepository.findByEmpresaIdAndPeriodoAcademico(1, "2026-2"))
                .thenReturn(List.of(practica("Ingeniería en Software")));

        component.exigirDisponibilidad(1, "2026-2", "Ingenieria en Software", 1);

        assertEquals(1, software.getCuposDisponibles());
        assertThrows(IllegalArgumentException.class,
                () -> component.exigirDisponibilidad(1, "2026-2", "Medicina", 1));
    }

    private OfertaCuposEmpresa ofertaGeneral(int total) {
        Empresa empresa = new Empresa();
        empresa.setId(1);
        empresa.setNombre("Empresa de prueba");
        OfertaCuposEmpresa oferta = new OfertaCuposEmpresa();
        oferta.setId(10);
        oferta.setEmpresa(empresa);
        oferta.setPeriodoAcademico("2026-2");
        oferta.setDistribucion("GENERAL");
        oferta.setCuposTotales(total);
        oferta.setActivo(true);
        return oferta;
    }

    private VacantePractica vacante(String nombreCarrera, int cupos) {
        VacantePractica vacante = new VacantePractica();
        vacante.setCarrera(nombreCarrera);
        vacante.setCupos(cupos);
        return vacante;
    }

    private Practica practica(String nombreCarrera) {
        Estudiante estudiante = new Estudiante();
        estudiante.setCarrera(nombreCarrera);
        Practica practica = new Practica();
        practica.setEstudiante(estudiante);
        return practica;
    }

    private Carrera carrera(String nombre) {
        Carrera carrera = new Carrera();
        carrera.setId(1);
        carrera.setNombre(nombre);
        carrera.setActivo(true);
        return carrera;
    }
}
