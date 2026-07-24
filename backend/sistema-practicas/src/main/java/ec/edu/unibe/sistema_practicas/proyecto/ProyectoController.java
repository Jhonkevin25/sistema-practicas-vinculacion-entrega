package ec.edu.unibe.sistema_practicas.proyecto;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.ofertacupofundacion.OfertaCuposFundacion;
import ec.edu.unibe.sistema_practicas.ofertacupofundacion.OfertaCuposFundacionComponent;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.vinculacion.PostulacionVinculacionRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/proyectos")
@RequiredArgsConstructor
public class ProyectoController {

    private static final String PROCESO = "VINCULACION";
    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "VIRTUAL", "HIBRIDA");

    private final ProyectoRepository proyectoRepository;
    private final FundacionRepository fundacionRepository;
    private final CarreraRepository carreraRepository;
    private final EstudianteRepository estudianteRepository;
    private final VinculacionRepository vinculacionRepository;
    private final PostulacionVinculacionRepository postulacionRepository;
    private final PeriodoAcademicoComponent periodoComponent;
    private final OfertaCuposFundacionComponent ofertaComponent;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final AlcanceCoordinador alcanceCoordinador;

    @GetMapping
    public List<Proyecto> getAll(
            @RequestParam(required = false) String periodo,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "COORDINADOR")
                && !alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        String periodoNormalizado = periodo == null || periodo.isBlank() ? null : periodo.trim();
        List<Proyecto> proyectos = periodoNormalizado == null
                ? proyectoRepository.findAll()
                : proyectoRepository.findByPeriodo(periodoNormalizado);
        return filtrarVisibles(proyectos, authentication, usuario);
    }

    // Fase 43: paginación y filtros sobre la consulta con alcance existente.
    // El recorte de visibilidad por rol/carrera/periodo sigue en filtrarVisibles.
    @GetMapping("/paginado")
    public PaginaResponse<Proyecto> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) Boolean estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        String buscado = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        java.util.stream.Stream<Proyecto> stream = getAll(periodo, authentication, usuario).stream()
                .filter(proyecto -> buscado.isEmpty()
                        || proyecto.getNombre().toLowerCase(Locale.ROOT).contains(buscado)
                        || (proyecto.getFundacion() != null && proyecto.getFundacion().getNombre() != null
                                && proyecto.getFundacion().getNombre().toLowerCase(Locale.ROOT).contains(buscado)))
                .filter(proyecto -> estado == null || estado.equals(proyecto.getEstado()));

        if ("id".equalsIgnoreCase(sortBy)) {
            if ("asc".equalsIgnoreCase(sortDir)) {
                stream = stream.sorted(java.util.Comparator.comparing(Proyecto::getId));
            } else {
                stream = stream.sorted(java.util.Comparator.comparing(Proyecto::getId).reversed());
            }
        } else if ("nombre".equalsIgnoreCase(sortBy)) {
            if ("asc".equalsIgnoreCase(sortDir)) {
                stream = stream.sorted(java.util.Comparator.comparing(Proyecto::getNombre, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            } else {
                stream = stream.sorted(java.util.Comparator.comparing(Proyecto::getNombre, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed());
            }
        }

        List<Proyecto> visibles = stream.toList();
        return PaginaResponse.deLista(visibles, pagina, tamano);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proyecto> getById(
            @PathVariable Integer id,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        return proyectoRepository.findById(id)
                .map(proyecto -> {
                    verificarPuedeVer(proyecto, authentication, usuario);
                    return ResponseEntity.ok(proyecto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fundacion/{fundacionId}")
    public List<Proyecto> getByFundacionId(
            @PathVariable Integer fundacionId,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "COORDINADOR")
                && !alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return List.of();
        }
        return filtrarVisibles(
                proyectoRepository.findByFundacionId(fundacionId), authentication, usuario);
    }

    @PostMapping
    @Transactional
    public Proyecto create(
            @Valid @RequestBody Proyecto details,
            Authentication authentication) {
        verificarGestion(authentication);
        Fundacion fundacion = exigirFundacionActiva(details);
        PeriodoAcademico periodo = periodoComponent.exigirConfigurable(details.getPeriodo());

        Proyecto proyecto = new Proyecto();
        proyecto.setFundacion(fundacion);
        proyecto.setPeriodo(periodo.getCodigo());
        normalizarDatosBasicos(proyecto, details);
        List<ProyectoCarrera> carreras = normalizarCarreras(proyecto, details, periodo, authentication);
        ConfiguracionCupos configuracion = configuracionSolicitada(details, carreras);
        OfertaCuposFundacion oferta = ofertaComponent.exigirDisponibilidad(
                fundacion.getId(), periodo.getCodigo(), configuracion.total(),
                configuracion.porCarrera(), null);
        aplicarCupos(proyecto, carreras, oferta, configuracion, Map.of());
        return proyectoRepository.save(proyecto);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Proyecto> update(
            @PathVariable Integer id,
            @Valid @RequestBody Proyecto details,
            Authentication authentication) {
        verificarGestion(authentication);
        return proyectoRepository.findByIdForUpdate(id)
                .map(proyecto -> {
                    verificarAlcanceProyecto(proyecto, authentication);
                    validarIdentidadInmutable(proyecto, details);
                    PeriodoAcademico periodo = periodoComponent.exigirConfigurable(proyecto.getPeriodo());
                    normalizarDatosBasicos(proyecto, details);
                    List<ProyectoCarrera> carreras = normalizarCarreras(
                            proyecto, details, periodo, authentication);
                    Map<String, Integer> ocupados = ocupadosPorCarrera(proyecto.getId());
                    ConfiguracionCupos configuracion = configuracionSolicitada(details, carreras);
                    OfertaCuposFundacion oferta = ofertaComponent.exigirDisponibilidad(
                            proyecto.getFundacion().getId(), proyecto.getPeriodo(), configuracion.total(),
                            configuracion.porCarrera(), proyecto.getId());
                    aplicarCupos(proyecto, carreras, oferta, configuracion, ocupados);
                    return ResponseEntity.ok(proyectoRepository.save(proyecto));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        verificarGestion(authentication);
        return proyectoRepository.findByIdForUpdate(id)
                .map(proyecto -> {
                    verificarAlcanceProyecto(proyecto, authentication);
                    if (!vinculacionRepository.findByProyectoId(id).isEmpty()
                            || postulacionRepository.existsByProyectoId(id)) {
                        throw new IllegalArgumentException(
                                "No se puede eliminar un proyecto con postulaciones o vinculaciones asociadas.");
                    }
                    proyectoRepository.delete(proyecto);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Fundacion exigirFundacionActiva(Proyecto details) {
        if (details == null || details.getFundacion() == null || details.getFundacion().getId() == null) {
            throw new IllegalArgumentException("Debes seleccionar una fundación para el proyecto.");
        }
        Fundacion fundacion = fundacionRepository.findById(details.getFundacion().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Fundación no encontrada."));
        if (Boolean.FALSE.equals(fundacion.getActiva())) {
            throw new IllegalArgumentException("La fundación seleccionada está desactivada.");
        }
        return fundacion;
    }

    private void normalizarDatosBasicos(Proyecto destino, Proyecto details) {
        destino.setNombre(textoRequerido(details.getNombre(), "El nombre del proyecto es obligatorio."));
        destino.setDescripcion(textoRequerido(
                details.getDescripcion(), "La descripción del proyecto es obligatoria."));
        destino.setCiudad(textoRequerido(details.getCiudad(), "La ciudad del proyecto es obligatoria."));
        if (destino.getCiudad().length() > 150) {
            throw new IllegalArgumentException("La ciudad no puede superar 150 caracteres.");
        }
        String modalidad = textoRequerido(
                details.getModalidad(), "La modalidad del proyecto es obligatoria.")
                .toUpperCase(Locale.ROOT);
        if (!MODALIDADES.contains(modalidad)) {
            throw new IllegalArgumentException("La modalidad debe ser PRESENCIAL, VIRTUAL o HIBRIDA.");
        }
        if (details.getHorasRequeridas() == null || details.getHorasRequeridas() <= 0) {
            throw new IllegalArgumentException("Las horas requeridas deben ser mayores que cero.");
        }
        destino.setModalidad(modalidad);
        destino.setHorasRequeridas(details.getHorasRequeridas());
        destino.setEstado(details.getEstado() == null || details.getEstado());
    }

    private List<ProyectoCarrera> normalizarCarreras(
            Proyecto proyecto,
            Proyecto details,
            PeriodoAcademico periodo,
            Authentication authentication) {
        if (details.getCarreras() == null || details.getCarreras().isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una carrera participante.");
        }
        Set<Integer> ids = new HashSet<>();
        List<ProyectoCarrera> normalizadas = new ArrayList<>();
        for (ProyectoCarrera item : details.getCarreras()) {
            if (item.getCarrera() == null || item.getCarrera().getId() == null) {
                throw new IllegalArgumentException("Todas las participaciones deben indicar una carrera válida.");
            }
            Carrera carrera = carreraRepository.findById(item.getCarrera().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Una carrera seleccionada no existe."));
            if (Boolean.FALSE.equals(carrera.getActivo())) {
                throw new IllegalArgumentException("No se puede usar una carrera inactiva.");
            }
            if (!ids.add(carrera.getId())) {
                throw new IllegalArgumentException("No repitas carreras en el proyecto.");
            }
            verificarCarreraCoordinador(carrera, authentication);
            convenioVigenteComponent.exigirParaFundacionEnPeriodo(
                    proyecto.getFundacion(), carrera.getNombre(), periodo);
            ProyectoCarrera normalizada = new ProyectoCarrera();
            normalizada.setProyecto(proyecto);
            normalizada.setCarrera(carrera);
            normalizada.setCuposTotales(item.getCuposTotales());
            normalizada.setCuposDisponibles(item.getCuposDisponibles());
            normalizadas.add(normalizada);
        }
        return normalizadas;
    }

    private ConfiguracionCupos configuracionSolicitada(
            Proyecto details,
            List<ProyectoCarrera> carreras) {
        Map<String, Integer> porCarrera = new LinkedHashMap<>();
        int suma = 0;
        for (ProyectoCarrera carrera : carreras) {
            if (carrera.getCuposTotales() != null) {
                if (carrera.getCuposTotales() <= 0) {
                    throw new IllegalArgumentException("Los cupos por carrera deben ser mayores que cero.");
                }
                porCarrera.put(carrera.getCarrera().getNombre(), carrera.getCuposTotales());
                suma += carrera.getCuposTotales();
            }
        }
        int total = details.getCuposTotales() == null ? 0 : details.getCuposTotales();
        if (total <= 0) total = suma;
        if (total <= 0) {
            throw new IllegalArgumentException("El proyecto debe reservar al menos un cupo.");
        }
        return new ConfiguracionCupos(total, porCarrera);
    }

    private void aplicarCupos(
            Proyecto proyecto,
            List<ProyectoCarrera> carreras,
            OfertaCuposFundacion oferta,
            ConfiguracionCupos configuracion,
            Map<String, Integer> ocupados) {
        int ocupadosTotales = ocupados.values().stream().mapToInt(Integer::intValue).sum();
        if ("GENERAL".equals(oferta.getDistribucion())) {
            if (configuracion.total() < ocupadosTotales) {
                throw new IllegalArgumentException("Los cupos totales no pueden ser menores que los "
                        + ocupadosTotales + " cupos ya ocupados del proyecto.");
            }
            for (String carreraOcupada : ocupados.keySet()) {
                if (carreras.stream().noneMatch(item -> OfertaCuposFundacionComponent.mismaCarrera(
                        item.getCarrera().getNombre(), carreraOcupada))) {
                    throw new IllegalArgumentException(
                            "No se puede retirar una carrera que ya tiene estudiantes asignados.");
                }
            }
            carreras.forEach(item -> {
                item.setCuposTotales(null);
                item.setCuposDisponibles(null);
            });
            proyecto.setCuposTotales(configuracion.total());
            proyecto.setCuposDisponibles(configuracion.total() - ocupadosTotales);
        } else {
            if (configuracion.porCarrera().size() != carreras.size()) {
                throw new IllegalArgumentException(
                        "La oferta está distribuida por carrera; asigna cupos a todas las carreras del proyecto.");
            }
            int total = 0;
            for (ProyectoCarrera item : carreras) {
                int asignados = configuracion.porCarrera().getOrDefault(item.getCarrera().getNombre(), 0);
                int usados = ocupados.entrySet().stream()
                        .filter(entry -> OfertaCuposFundacionComponent.mismaCarrera(
                                entry.getKey(), item.getCarrera().getNombre()))
                        .mapToInt(Map.Entry::getValue)
                        .sum();
                if (asignados < usados) {
                    throw new IllegalArgumentException("Los cupos de " + item.getCarrera().getNombre()
                            + " no pueden ser menores que los " + usados + " ya ocupados.");
                }
                item.setCuposTotales(asignados);
                item.setCuposDisponibles(asignados - usados);
                total += asignados;
            }
            for (String carreraOcupada : ocupados.keySet()) {
                if (carreras.stream().noneMatch(item -> OfertaCuposFundacionComponent.mismaCarrera(
                        item.getCarrera().getNombre(), carreraOcupada))) {
                    throw new IllegalArgumentException(
                            "No se puede retirar una carrera que ya tiene estudiantes asignados.");
                }
            }
            proyecto.setCuposTotales(total);
            proyecto.setCuposDisponibles(total - ocupadosTotales);
        }
        proyecto.getCarreras().clear();
        proyecto.getCarreras().addAll(carreras);
    }

    private Map<String, Integer> ocupadosPorCarrera(Integer proyectoId) {
        Map<String, Integer> ocupados = new LinkedHashMap<>();
        for (Vinculacion vinculacion : vinculacionRepository.findByProyectoId(proyectoId)) {
            if (vinculacion.getEstudiante() != null) {
                ocupados.merge(vinculacion.getEstudiante().getCarrera(), 1, Integer::sum);
            }
        }
        return ocupados;
    }

    private void validarIdentidadInmutable(Proyecto proyecto, Proyecto details) {
        if (details.getFundacion() != null && details.getFundacion().getId() != null
                && !Objects.equals(proyecto.getFundacion().getId(), details.getFundacion().getId())) {
            throw new IllegalArgumentException("No se puede cambiar la fundación de un proyecto existente.");
        }
        if (details.getPeriodo() != null && !details.getPeriodo().isBlank()
                && !proyecto.getPeriodo().equals(details.getPeriodo().trim())) {
            throw new IllegalArgumentException("No se puede cambiar el periodo de un proyecto existente.");
        }
    }

    private List<Proyecto> filtrarVisibles(
            List<Proyecto> proyectos,
            Authentication authentication,
            Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return proyectos;
        if (hasRole(authentication, "COORDINADOR")) {
            return proyectos.stream()
                    .filter(proyecto -> proyectoVisibleParaCoordinador(proyecto, authentication))
                    .toList();
        }
        if (hasRole(authentication, "ESTUDIANTE")) {
            Optional<Estudiante> estudiante = usuario == null
                    ? Optional.empty()
                    : estudianteRepository.findByUsuarioEmail(usuario.getEmail());
            if (estudiante.isEmpty()) return List.of();
            String periodoActivo = periodoComponent.activo().getCodigo();
            return proyectos.stream()
                    .filter(proyecto -> Boolean.TRUE.equals(proyecto.getEstado()))
                    .filter(proyecto -> periodoActivo.equals(proyecto.getPeriodo()))
                    .filter(proyecto -> proyecto.getCuposDisponibles() != null
                            && proyecto.getCuposDisponibles() > 0)
                    .filter(proyecto -> participaCarrera(proyecto, estudiante.get().getCarrera()))
                    .toList();
        }
        if (hasRole(authentication, "TUTOR") && usuario != null) {
            Set<Integer> proyectosAsignados = vinculacionRepository.findByTutorId(usuario.getId()).stream()
                    .map(Vinculacion::getProyecto)
                    .filter(Objects::nonNull)
                    .map(Proyecto::getId)
                    .collect(java.util.stream.Collectors.toSet());
            return proyectos.stream().filter(proyecto -> proyectosAsignados.contains(proyecto.getId())).toList();
        }
        return List.of();
    }

    private void verificarPuedeVer(
            Proyecto proyecto,
            Authentication authentication,
            Usuario usuario) {
        if (filtrarVisibles(List.of(proyecto), authentication, usuario).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este proyecto.");
        }
    }

    private void verificarGestion(Authentication authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "COORDINADOR")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar proyectos.");
        }
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no tiene acceso a proyectos de Vinculación.");
        }
    }

    private void verificarAlcanceProyecto(Proyecto proyecto, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (!proyectoGestionable(proyecto, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes gestionar un proyecto con carreras fuera de tu alcance.");
        }
    }

    private boolean proyectoGestionable(Proyecto proyecto, Authentication authentication) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)
                || proyecto.getCarreras() == null
                || proyecto.getCarreras().isEmpty()) {
            return false;
        }
        Set<String> visibles = alcanceCoordinador.carrerasVisibles(authentication).orElse(Set.of());
        return proyecto.getCarreras().stream()
                .allMatch(item -> item.getCarrera() != null && visibles.stream()
                        .anyMatch(carrera -> OfertaCuposFundacionComponent.mismaCarrera(
                                carrera, item.getCarrera().getNombre())));
    }

    private boolean proyectoVisibleParaCoordinador(
            Proyecto proyecto,
            Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)
                || proyecto.getCarreras() == null
                || proyecto.getCarreras().isEmpty()) {
            return false;
        }
        Set<String> visibles = alcanceCoordinador.carrerasVisibles(authentication).orElse(Set.of());
        return proyecto.getCarreras().stream()
                .anyMatch(item -> item.getCarrera() != null && visibles.stream()
                        .anyMatch(carrera -> OfertaCuposFundacionComponent.mismaCarrera(
                                carrera, item.getCarrera().getNombre())));
    }

    private void verificarCarreraCoordinador(Carrera carrera, Authentication authentication) {
        if (!hasRole(authentication, "COORDINADOR")) return;
        boolean visible = alcanceCoordinador.carrerasVisibles(authentication)
                .orElse(Set.of())
                .stream()
                .anyMatch(nombre -> OfertaCuposFundacionComponent.mismaCarrera(
                        nombre, carrera.getNombre()));
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes gestionar proyectos para " + carrera.getNombre() + ".");
        }
    }

    private boolean participaCarrera(Proyecto proyecto, String carrera) {
        return proyecto.getCarreras() != null && proyecto.getCarreras().stream()
                .anyMatch(item -> item.getCarrera() != null
                        && OfertaCuposFundacionComponent.mismaCarrera(
                                item.getCarrera().getNombre(), carrera));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private String textoRequerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
        return valor.trim();
    }

    private record ConfiguracionCupos(int total, Map<String, Integer> porCarrera) {
    }
}
