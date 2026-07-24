package ec.edu.unibe.sistema_practicas.vacante;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaComponent;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/vacantes")
@RequiredArgsConstructor
public class VacantePracticaController {

    private static final String PROCESO = "PRACTICAS";
    private static final Set<String> MODALIDADES_ACADEMICAS = Set.of("Práctica I", "Práctica II");

    private final VacantePracticaRepository vacantePracticaRepository;
    private final EmpresaRepository empresaRepository;
    private final CarreraRepository carreraRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final OfertaCuposEmpresaComponent ofertaCuposEmpresaComponent;

    @GetMapping
    public List<VacantePractica> getAll(
            @RequestParam(required = false) String periodoAcademico,
            Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        String periodo = periodoAcademico == null || periodoAcademico.isBlank()
                ? null
                : periodoAcademico.trim();
        List<VacantePractica> vacantes = alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<VacantePractica>of()
                        : periodo == null
                                ? vacantePracticaRepository.findByCarreraIn(carreras)
                                : vacantePracticaRepository.findByCarreraInAndPeriodoAcademico(carreras, periodo))
                .orElseGet(() -> periodo == null
                        ? vacantePracticaRepository.findAll()
                        : vacantePracticaRepository.findByPeriodoAcademico(periodo));
        // Fase 42: los estudiantes no ven vacantes pausadas; gestión sí
        if (esEstudiante(authentication)) {
            return vacantes.stream()
                    .filter(vacante -> !Boolean.FALSE.equals(vacante.getActiva()))
                    .toList();
        }
        return vacantes;
    }

    // Fase 43: paginación y filtros sobre la consulta con alcance existente
    // (estudiantes ya no reciben pausadas; el coordinador conserva su alcance).
    @GetMapping("/paginado")
    public PaginaResponse<VacantePractica> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String periodoAcademico,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) Boolean activa,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            Authentication authentication) {
        String buscado = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        List<VacantePractica> visibles = getAll(periodoAcademico, authentication).stream()
                .filter(vacante -> buscado.isEmpty()
                        || vacante.getNombre().toLowerCase(Locale.ROOT).contains(buscado)
                        || (vacante.getEmpresa() != null && vacante.getEmpresa().getNombre() != null
                                && vacante.getEmpresa().getNombre().toLowerCase(Locale.ROOT).contains(buscado)))
                .filter(vacante -> carrera == null || carrera.isBlank()
                        || OfertaCuposEmpresaComponent.mismaCarrera(vacante.getCarrera(), carrera))
                .filter(vacante -> activa == null
                        || activa.equals(vacante.getActiva() == null || vacante.getActiva()))
                .toList();
        return PaginaResponse.deLista(visibles, pagina, tamano);
    }

    @PostMapping
    @Transactional
    public VacantePractica create(@Valid @RequestBody VacantePractica vacante,
                                  Authentication authentication) {
        normalizarYValidar(vacante, authentication, null);
        VacantePractica saved = vacantePracticaRepository.save(vacante);
        return vacantePracticaRepository.findById(saved.getId()).orElse(saved);
    }

    // Fase 42: edición de vacantes existentes. Empresa y periodo son inmutables;
    // los cupos se validan contra la oferta excluyendo la reserva propia.
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<VacantePractica> update(@PathVariable Integer id,
                                                  @Valid @RequestBody VacantePractica details,
                                                  Authentication authentication) {
        return vacantePracticaRepository.findByIdForUpdate(id)
                .map(vacante -> {
                    verificarAlcance(vacante, authentication);
                    if (details.getEmpresa() != null && details.getEmpresa().getId() != null
                            && !details.getEmpresa().getId().equals(vacante.getEmpresa().getId())) {
                        throw new IllegalArgumentException("No se puede cambiar la empresa de una vacante existente.");
                    }
                    if (details.getPeriodoAcademico() != null && !details.getPeriodoAcademico().isBlank()
                            && !vacante.getPeriodoAcademico().equals(details.getPeriodoAcademico().trim())) {
                        throw new IllegalArgumentException("No se puede cambiar el periodo de una vacante existente.");
                    }
                    details.setEmpresa(vacante.getEmpresa());
                    details.setPeriodoAcademico(vacante.getPeriodoAcademico());
                    normalizarYValidar(details, authentication, vacante.getId());
                    vacante.setNombre(details.getNombre());
                    vacante.setCupos(details.getCupos());
                    vacante.setHoras(details.getHoras());
                    vacante.setDescripcion(details.getDescripcion());
                    vacante.setCarrera(details.getCarrera());
                    vacante.setModalidadAcademica(details.getModalidadAcademica());
                    vacante.setArea(details.getArea());
                    vacante.setCiudad(details.getCiudad());
                    vacante.setTipoEmpresa(details.getTipoEmpresa());
                    vacante.setModalidadTrabajo(details.getModalidadTrabajo());
                    vacante.setFechaLimite(details.getFechaLimite());
                    vacante.setAltaDemanda(details.getAltaDemanda());
                    vacante.setRequisitos(details.getRequisitos());
                    vacante.setPerfilRequerido(details.getPerfilRequerido());
                    if (details.getActiva() != null) {
                        vacante.setActiva(details.getActiva());
                    }
                    return ResponseEntity.ok(vacantePracticaRepository.save(vacante));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Fase 42: pausa/reactivación sin exigir revalidar fechas ni catálogos.
    // Pausar oculta la vacante a estudiantes y bloquea asignaciones; los cupos
    // reservados NO se devuelven a la empresa.
    @PostMapping("/{id}/pausar")
    @Transactional
    public VacantePractica pausar(@PathVariable Integer id, Authentication authentication) {
        return cambiarActivacion(id, false, authentication);
    }

    @PostMapping("/{id}/reactivar")
    @Transactional
    public VacantePractica reactivar(@PathVariable Integer id, Authentication authentication) {
        return cambiarActivacion(id, true, authentication);
    }

    private VacantePractica cambiarActivacion(Integer id, boolean activa, Authentication authentication) {
        VacantePractica vacante = vacantePracticaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacante no encontrada."));
        verificarAlcance(vacante, authentication);
        vacante.setActiva(activa);
        return vacantePracticaRepository.save(vacante);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        return vacantePracticaRepository.findById(id)
                .map(vacante -> {
                    verificarAlcance(vacante, authentication);
                    vacantePracticaRepository.delete(vacante);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void normalizarYValidar(VacantePractica vacante, Authentication authentication,
                                    Integer vacanteExcluidaId) {
        if (vacante.getEmpresa() == null || vacante.getEmpresa().getId() == null) {
            throw new IllegalArgumentException("Selecciona una empresa válida para la vacante.");
        }
        Empresa empresa = empresaRepository.findById(vacante.getEmpresa().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada."));
        if (Boolean.FALSE.equals(empresa.getActivo())) {
            throw new IllegalArgumentException("La empresa seleccionada está inactiva.");
        }

        String nombreCarrera = textoRequerido(vacante.getCarrera(), "La carrera autorizada es obligatoria.");
        Carrera carrera = carreraRepository.findByNombreIgnoreCase(nombreCarrera)
                .orElseThrow(() -> new IllegalArgumentException("La carrera seleccionada no existe en el catálogo."));
        if (Boolean.FALSE.equals(carrera.getActivo())) {
            throw new IllegalArgumentException("La carrera seleccionada está inactiva.");
        }

        vacante.setEmpresa(empresa);
        vacante.setCarrera(carrera.getNombre());
        verificarAlcance(vacante, authentication);
        convenioVigenteComponent.exigirParaEmpresa(empresa, carrera.getNombre());

        if (vacante.getCupos() == null || vacante.getCupos() <= 0) {
            throw new IllegalArgumentException("La vacante debe publicar al menos un cupo.");
        }
        String periodo = textoRequerido(
                vacante.getPeriodoAcademico(), "El periodo académico de la vacante es obligatorio.");
        if (periodo.length() > 20) {
            throw new IllegalArgumentException("El periodo académico no puede superar 20 caracteres.");
        }
        vacante.setPeriodoAcademico(periodo);
        ofertaCuposEmpresaComponent.exigirDisponibilidad(
                empresa.getId(), periodo, carrera.getNombre(), vacante.getCupos(), vacanteExcluidaId);
        if (vacante.getHoras() == null || vacante.getHoras() <= 0) {
            throw new IllegalArgumentException("Las horas requeridas deben ser mayores que cero.");
        }
        if (!MODALIDADES_ACADEMICAS.contains(vacante.getModalidadAcademica())) {
            throw new IllegalArgumentException("La modalidad académica debe ser Práctica I o Práctica II.");
        }
        if (vacante.getFechaLimite() == null || vacante.getFechaLimite().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha límite de la vacante no puede estar vencida.");
        }

        vacante.setNombre(textoRequerido(vacante.getNombre(), "El nombre de la vacante es obligatorio."));
        vacante.setArea(textoRequerido(vacante.getArea(), "El campo profesional es obligatorio."));
        vacante.setDescripcion(textoRequerido(vacante.getDescripcion(), "Describe las actividades de la vacante."));
        vacante.setPerfilRequerido(textoRequerido(
                vacante.getPerfilRequerido(), "El perfil profesional requerido es obligatorio."));
        vacante.setCiudad(textoRequerido(vacante.getCiudad(), "La ciudad es obligatoria."));
        vacante.setModalidadTrabajo(textoRequerido(
                vacante.getModalidadTrabajo(), "La modalidad de trabajo es obligatoria."));
        vacante.setTipoEmpresa(textoRequerido(vacante.getTipoEmpresa(), "El tipo de empresa es obligatorio."));
        vacante.setRequisitos(vacioANull(vacante.getRequisitos()));
    }

    private String textoRequerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private boolean esEstudiante(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ESTUDIANTE".equals(a.getAuthority()));
    }

    private void verificarAlcance(VacantePractica vacante, Authentication authentication) {
        boolean coordinador = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COORDINADOR".equals(a.getAuthority()));
        if (!coordinador) return;
        boolean visible = alcanceCoordinador.procesoVisible(authentication, PROCESO)
                && alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> vacante != null && carreras.contains(vacante.getCarrera()))
                .orElse(false);
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar esta vacante.");
        }
    }
}
