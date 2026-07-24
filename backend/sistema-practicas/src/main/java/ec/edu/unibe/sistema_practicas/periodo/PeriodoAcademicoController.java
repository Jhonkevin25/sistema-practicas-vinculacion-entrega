package ec.edu.unibe.sistema_practicas.periodo;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.postulacion.PostulacionMeritocratica;
import ec.edu.unibe.sistema_practicas.postulacion.PostulacionMeritocraticaRepository;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacionRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/configuracion/periodos")
@RequiredArgsConstructor
public class PeriodoAcademicoController {

    private static final Set<String> ESTADOS_EXPEDIENTE_ACTIVO = Set.of("pendiente", "en_curso");

    private final PeriodoAcademicoRepository periodoRepository;
    private final PeriodoAcademicoComponent periodoComponent;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final PostulacionVinculacionRepository postulacionVinculacionRepository;
    private final PostulacionMeritocraticaRepository postulacionMeritocraticaRepository;
    private final AuditoriaEmitter auditoriaEmitter;

    @GetMapping
    public List<PeriodoAcademico> getAll() {
        return periodoRepository.findAllByOrderByFechaInicioDescIdDesc();
    }

    @GetMapping("/activo")
    public PeriodoAcademico getActivo() {
        return periodoComponent.activo();
    }

    @PostMapping
    @Transactional
    public PeriodoAcademico create(@Valid @RequestBody PeriodoAcademico details) {
        PeriodoAcademico periodo = new PeriodoAcademico();
        normalizar(periodo, details, false);
        if (periodoRepository.findByCodigo(periodo.getCodigo()).isPresent()) {
            throw new IllegalArgumentException("Ya existe ese periodo académico.");
        }
        validarUnicoActivo(periodo, null);
        return periodoRepository.save(periodo);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<PeriodoAcademico> update(
            @PathVariable Integer id,
            @Valid @RequestBody PeriodoAcademico details) {
        return periodoRepository.findById(id)
                .map(periodo -> {
                    if ("CERRADO".equals(periodo.getEstado())) {
                        throw new IllegalArgumentException(
                                "Un periodo cerrado no puede modificarse ni reabrirse.");
                    }
                    if ("ACTIVO".equals(periodo.getEstado())
                            && "PLANIFICADO".equals(periodoComponent.normalizarEstado(details.getEstado()))) {
                        throw new IllegalArgumentException(
                                "Un periodo activo no puede volver a planificado; usa el cierre formal.");
                    }
                    normalizar(periodo, details, true);
                    validarUnicoActivo(periodo, id);
                    return ResponseEntity.ok(periodoRepository.save(periodo));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Fase 42: cierre formal del periodo. Bloquea nuevas postulaciones y
    // asignaciones (los flujos exigen periodo ACTIVO), expira las postulaciones
    // pendientes y no permite cerrar con expedientes activos sin resolver.
    // Los cupos sobrantes no se trasladan: las ofertas son por periodo.
    @PostMapping("/{id}/cerrar")
    @Transactional
    public PeriodoAcademico cerrar(@PathVariable Integer id,
                                   @AuthenticationPrincipal Usuario usuario) {
        PeriodoAcademico periodo = periodoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Periodo académico no encontrado."));
        if ("CERRADO".equals(periodo.getEstado())) {
            return periodo;
        }
        String codigo = periodo.getCodigo();
        long practicasActivas = practicaRepository
                .countByPeriodoAcademicoAndEstadoIn(codigo, ESTADOS_EXPEDIENTE_ACTIVO);
        long vinculacionesActivas = vinculacionRepository
                .countByPeriodoAcademicoAndEstadoIn(codigo, ESTADOS_EXPEDIENTE_ACTIVO);
        if (practicasActivas > 0 || vinculacionesActivas > 0) {
            throw new IllegalArgumentException("No se puede cerrar el periodo " + codigo + ": tiene "
                    + practicasActivas + " práctica(s) y " + vinculacionesActivas
                    + " vinculación(es) activas sin resolver (ciérralas o finalízalas primero).");
        }

        int expiradasVinculacion = 0;
        for (PostulacionVinculacion postulacion
                : postulacionVinculacionRepository.findByEstadoAndPeriodoAcademico("Pendiente", codigo)) {
            postulacion.setEstado("Expirada");
            postulacion.setObservacion("Postulación expirada por cierre del periodo " + codigo + ".");
            postulacionVinculacionRepository.save(postulacion);
            expiradasVinculacion++;
        }
        int expiradasMerito = 0;
        for (PostulacionMeritocratica postulacion : postulacionMeritocraticaRepository.findByEstado("Pendiente")) {
            if (perteneceAlPeriodo(postulacion, codigo)) {
                postulacion.setEstado("Expirada");
                postulacionMeritocraticaRepository.save(postulacion);
                expiradasMerito++;
            }
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("codigo", codigo);
        antes.put("estado", periodo.getEstado());
        periodo.setEstado("CERRADO");
        PeriodoAcademico cerrado = periodoRepository.save(periodo);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("codigo", codigo);
        despues.put("estado", "CERRADO");
        despues.put("postulacionesVinculacionExpiradas", expiradasVinculacion);
        despues.put("postulacionesMeritocraticasExpiradas", expiradasMerito);
        auditoriaEmitter.registrar("PERIODOS_ACADEMICOS", "CIERRE_PERIODO", usuario, antes, despues);
        return cerrado;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return periodoRepository.findById(id)
                .map(periodo -> {
                    if (!"PLANIFICADO".equals(periodo.getEstado())) {
                        throw new IllegalArgumentException(
                                "Solo se puede eliminar un periodo planificado y todavía no utilizado.");
                    }
                    try {
                        periodoRepository.delete(periodo);
                        periodoRepository.flush();
                    } catch (DataIntegrityViolationException ex) {
                        throw new IllegalArgumentException(
                                "No se puede eliminar el periodo porque ya tiene información académica asociada.");
                    }
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Una postulación pertenece al periodo si CUALQUIERA de sus preferencias
    // apunta a una vacante de ese periodo (mirar solo pref1 dejaba pendientes
    // huérfanas cuando las preferencias mezclaban periodos).
    private boolean perteneceAlPeriodo(PostulacionMeritocratica postulacion, String codigo) {
        return Stream.of(postulacion.getPref1(), postulacion.getPref2(), postulacion.getPref3())
                .filter(Objects::nonNull)
                .anyMatch(vacante -> codigo.equals(vacante.getPeriodoAcademico()));
    }

    private void normalizar(PeriodoAcademico destino, PeriodoAcademico details, boolean actualizacion) {
        String codigo = periodoComponent.normalizarCodigo(details.getCodigo());
        if (actualizacion && !destino.getCodigo().equals(codigo)) {
            throw new IllegalArgumentException("No se puede cambiar el código de un periodo existente.");
        }
        if (details.getFechaInicio() == null || details.getFechaFin() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin del periodo son obligatorias.");
        }
        if (details.getFechaInicio().isAfter(details.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
        String estado = periodoComponent.normalizarEstado(details.getEstado());
        if ("CERRADO".equals(estado)) {
            throw new IllegalArgumentException(
                    "El cierre de un periodo se hace con su acción de cierre formal, no editándolo.");
        }
        destino.setCodigo(codigo);
        destino.setFechaInicio(details.getFechaInicio());
        destino.setFechaFin(details.getFechaFin());
        destino.setEstado(estado);
    }

    private void validarUnicoActivo(PeriodoAcademico periodo, Integer idActual) {
        if (!"ACTIVO".equals(periodo.getEstado())) return;
        periodoRepository.findByEstado("ACTIVO")
                .filter(actual -> idActual == null || !actual.getId().equals(idActual))
                .ifPresent(actual -> {
                    throw new IllegalArgumentException(
                            "Ya existe un periodo activo: " + actual.getCodigo() + ".");
                });
    }
}
