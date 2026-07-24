package ec.edu.unibe.sistema_practicas.periodo;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.postulacion.PostulacionMeritocratica;
import ec.edu.unibe.sistema_practicas.postulacion.PostulacionMeritocraticaRepository;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacionRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeriodoAcademicoControllerTests {

    @Mock private PeriodoAcademicoRepository periodoRepository;
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private PracticaRepository practicaRepository;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private PostulacionVinculacionRepository postulacionVinculacionRepository;
    @Mock private PostulacionMeritocraticaRepository postulacionMeritocraticaRepository;
    @Mock private AuditoriaEmitter auditoriaEmitter;

    @InjectMocks
    private PeriodoAcademicoController controller;

    @Test
    void cerrar_bloquea_si_hay_expedientes_activos_sin_resolver() {
        PeriodoAcademico periodo = periodo("2026-2", "ACTIVO");
        when(periodoRepository.findById(3)).thenReturn(Optional.of(periodo));
        when(practicaRepository.countByPeriodoAcademicoAndEstadoIn(eq("2026-2"), anyCollection()))
                .thenReturn(1L);
        when(vinculacionRepository.countByPeriodoAcademicoAndEstadoIn(eq("2026-2"), anyCollection()))
                .thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> controller.cerrar(3, null));

        verify(periodoRepository, never()).save(any());
    }

    @Test
    void cerrar_expira_postulaciones_pendientes_del_periodo_y_cierra() {
        PeriodoAcademico periodo = periodo("2026-2", "ACTIVO");
        when(periodoRepository.findById(3)).thenReturn(Optional.of(periodo));
        when(practicaRepository.countByPeriodoAcademicoAndEstadoIn(anyString(), anyCollection()))
                .thenReturn(0L);
        when(vinculacionRepository.countByPeriodoAcademicoAndEstadoIn(anyString(), anyCollection()))
                .thenReturn(0L);

        PostulacionVinculacion pendienteVinculacion = new PostulacionVinculacion();
        pendienteVinculacion.setEstado("Pendiente");
        when(postulacionVinculacionRepository.findByEstadoAndPeriodoAcademico("Pendiente", "2026-2"))
                .thenReturn(List.of(pendienteVinculacion));

        PostulacionMeritocratica delPeriodo = postulacionMeritocratica("2026-2");
        PostulacionMeritocratica deOtroPeriodo = postulacionMeritocratica("2027-1");
        when(postulacionMeritocraticaRepository.findByEstado("Pendiente"))
                .thenReturn(List.of(delPeriodo, deOtroPeriodo));
        when(periodoRepository.save(periodo)).thenReturn(periodo);

        PeriodoAcademico cerrado = controller.cerrar(3, null);

        assertEquals("CERRADO", cerrado.getEstado());
        assertEquals("Expirada", pendienteVinculacion.getEstado());
        assertEquals("Expirada", delPeriodo.getEstado());
        assertEquals("Pendiente", deOtroPeriodo.getEstado());
        verify(auditoriaEmitter).registrar(eq("PERIODOS_ACADEMICOS"), eq("CIERRE_PERIODO"),
                any(), any(), any());
    }

    @Test
    void cerrar_expira_postulacion_si_cualquier_preferencia_es_del_periodo() {
        PeriodoAcademico periodo = periodo("2026-2", "ACTIVO");
        when(periodoRepository.findById(3)).thenReturn(Optional.of(periodo));
        when(practicaRepository.countByPeriodoAcademicoAndEstadoIn(anyString(), anyCollection()))
                .thenReturn(0L);
        when(vinculacionRepository.countByPeriodoAcademicoAndEstadoIn(anyString(), anyCollection()))
                .thenReturn(0L);

        PostulacionMeritocratica prefsMezcladas = postulacionMeritocratica("2027-1");
        prefsMezcladas.setPref2(vacante("2026-2"));
        PostulacionMeritocratica deOtroPeriodo = postulacionMeritocratica("2027-1");
        deOtroPeriodo.setPref2(vacante("2027-1"));
        when(postulacionMeritocraticaRepository.findByEstado("Pendiente"))
                .thenReturn(List.of(prefsMezcladas, deOtroPeriodo));
        when(periodoRepository.save(periodo)).thenReturn(periodo);

        controller.cerrar(3, null);

        assertEquals("Expirada", prefsMezcladas.getEstado());
        assertEquals("Pendiente", deOtroPeriodo.getEstado());
    }

    @Test
    void cerrar_es_idempotente_para_un_periodo_ya_cerrado() {
        PeriodoAcademico periodo = periodo("2026-1", "CERRADO");
        when(periodoRepository.findById(2)).thenReturn(Optional.of(periodo));

        PeriodoAcademico resultado = controller.cerrar(2, null);

        assertEquals("CERRADO", resultado.getEstado());
        verify(periodoRepository, never()).save(any());
        verify(practicaRepository, never())
                .countByPeriodoAcademicoAndEstadoIn(anyString(), anyCollection());
    }

    private PeriodoAcademico periodo(String codigo, String estado) {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(3);
        periodo.setCodigo(codigo);
        periodo.setEstado(estado);
        periodo.setFechaInicio(LocalDate.of(2026, 7, 1));
        periodo.setFechaFin(LocalDate.of(2026, 12, 31));
        return periodo;
    }

    private PostulacionMeritocratica postulacionMeritocratica(String periodoVacante) {
        PostulacionMeritocratica postulacion = new PostulacionMeritocratica();
        postulacion.setEstado("Pendiente");
        postulacion.setPref1(vacante(periodoVacante));
        return postulacion;
    }

    private VacantePractica vacante(String periodoAcademico) {
        VacantePractica vacante = new VacantePractica();
        vacante.setId(periodoAcademico.hashCode());
        vacante.setPeriodoAcademico(periodoAcademico);
        return vacante;
    }
}
