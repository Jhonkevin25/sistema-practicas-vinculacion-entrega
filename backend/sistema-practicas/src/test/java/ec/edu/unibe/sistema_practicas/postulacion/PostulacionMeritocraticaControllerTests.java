package ec.edu.unibe.sistema_practicas.postulacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademicaRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostulacionMeritocraticaControllerTests {

    @Mock private PostulacionMeritocraticaRepository postulacionMeritocraticaRepository;
    @Mock private FechasConvocatoriaRepository fechasConvocatoriaRepository;
    @Mock private PracticaRepository practicaRepository;
    @Mock private DocEstudianteRepository documentoRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private VacantePracticaRepository vacantePracticaRepository;
    @Mock private NotaAcademicaRepository notaAcademicaRepository;
    @Mock private AuditoriaEmitter auditoriaEmitter;
    @Mock private VinculacionRepository vinculacionRepository;
    @Mock private ConvenioVigenteComponent convenioVigenteComponent;
    @Mock private NotificacionEmitter notificacionEmitter;
    // Fase 42: descontarCupoVacante exige periodo activo antes de consumir cupos
    @Mock private PeriodoAcademicoComponent periodoComponent;
    @Mock private TutorAsignacionComponent tutorAsignacionComponent;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PostulacionMeritocraticaController controller;

    @Test
    void ajuste_manual_registra_vacante_justificacion_y_actor() {
        Usuario coordinador = new Usuario();
        coordinador.setId(3);
        VacantePractica vacante = new VacantePractica();
        vacante.setId(12);
        vacante.setNombre("Desarrollo backend");
        PostulacionMeritocratica postulacion = new PostulacionMeritocratica();
        postulacion.setId(25);
        postulacion.setJustificacionAjuste("Perfil técnico validado por coordinación.");

        controller.registrarAuditoriaAjuste(postulacion, vacante, coordinador);

        verify(auditoriaEmitter).registrar(
                eq("POSTULACIONES_MERITOCRATICAS"), eq("AJUSTE_MANUAL_ASIGNACION"),
                eq(coordinador), eq(null), anyMap());
    }
}
