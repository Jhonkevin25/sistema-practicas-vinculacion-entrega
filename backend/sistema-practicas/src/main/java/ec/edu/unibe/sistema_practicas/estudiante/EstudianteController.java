package ec.edu.unibe.sistema_practicas.estudiante;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/estudiantes")
@RequiredArgsConstructor
public class EstudianteController {

    private final EstudianteRepository estudianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;

    @GetMapping
    public List<Estudiante> getAll(Authentication authentication,
                                   @AuthenticationPrincipal Usuario usuario) {
        if (hasRole(authentication, "TUTOR")) {
            if (usuario == null) return List.of();
            Map<Integer, Estudiante> asignados = new LinkedHashMap<>();
            practicaRepository.findByTutorId(usuario.getId()).stream()
                    .filter(p -> p.getEstudiante() != null)
                    .forEach(p -> asignados.put(p.getEstudiante().getId(), p.getEstudiante()));
            vinculacionRepository.findByTutorId(usuario.getId()).stream()
                    .filter(v -> v.getEstudiante() != null)
                    .forEach(v -> asignados.put(v.getEstudiante().getId(), v.getEstudiante()));
            return asignados.values().stream().toList();
        }
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.isEmpty()
                        ? List.<Estudiante>of()
                        : estudianteRepository.findByCarreraIn(carreras))
                .orElseGet(estudianteRepository::findAll);
    }

    // Fase 43: listado paginado con filtros (texto, carrera, semestre). El
    // alcance del coordinador viaja dentro de la consulta; el tutor pagina
    // sobre sus estudiantes asignados (lista corta por diseño).
    @GetMapping("/paginado")
    public PaginaResponse<Estudiante> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) Integer semestre,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication,
            @AuthenticationPrincipal Usuario usuario) {
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort;
        if (sortBy.equalsIgnoreCase("nombre") || sortBy.equalsIgnoreCase("apellido")) {
            sort = Sort.by(direction, "usuario.apellido").and(Sort.by(direction, "usuario.nombre"));
        } else {
            // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
            String campoOrden = List.of("id", "matricula", "carrera", "semestre").contains(sortBy) ? sortBy : "id";
            sort = Sort.by(direction, campoOrden);
        }

        if (hasRole(authentication, "TUTOR")) {
            List<Estudiante> propios = getAll(authentication, usuario).stream()
                    .filter(est -> coincideTexto(est, texto))
                    .filter(est -> carrera == null || carrera.isBlank()
                            || carrera.trim().equalsIgnoreCase(est.getCarrera()))
                    .filter(est -> semestre == null || semestre.equals(est.getSemestre()))
                    .toList();
            return PaginaResponse.deLista(propios, pagina, tamano);
        }
        Optional<Set<String>> alcance = alcanceCoordinador.carrerasVisibles(authentication);
        if (alcance.isPresent() && alcance.get().isEmpty()) {
            return PaginaResponse.deLista(List.of(), pagina, tamano);
        }
        Specification<Estudiante> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            alcance.ifPresent(carreras -> condiciones.add(root.get("carrera").in(carreras)));
            if (texto != null && !texto.isBlank()) {
                String like = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                Join<Estudiante, Usuario> joinUsuario = root.join("usuario");
                condiciones.add(cb.or(
                        cb.like(cb.lower(joinUsuario.get("nombre")), like),
                        cb.like(cb.lower(joinUsuario.get("apellido")), like),
                        cb.like(cb.lower(joinUsuario.get("email")), like),
                        cb.like(cb.lower(root.get("matricula")), like)));
            }
            if (carrera != null && !carrera.isBlank()) {
                condiciones.add(cb.equal(cb.lower(root.get("carrera")), carrera.trim().toLowerCase(Locale.ROOT)));
            }
            if (semestre != null) {
                condiciones.add(cb.equal(root.get("semestre"), semestre));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        return PaginaResponse.desde(estudianteRepository.findAll(spec, PaginaResponse.pageable(
                pagina, tamano, sort)));
    }

    private boolean coincideTexto(Estudiante estudiante, String texto) {
        if (texto == null || texto.isBlank()) return true;
        String buscado = texto.trim().toLowerCase(Locale.ROOT);
        String nombre = estudiante.getUsuario() == null ? "" :
                (estudiante.getUsuario().getNombre() + " " + estudiante.getUsuario().getApellido()
                        + " " + estudiante.getUsuario().getEmail());
        return nombre.toLowerCase(Locale.ROOT).contains(buscado)
                || (estudiante.getMatricula() != null
                        && estudiante.getMatricula().toLowerCase(Locale.ROOT).contains(buscado));
    }

    @GetMapping("/me")
    public ResponseEntity<Estudiante> getMe(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return estudianteRepository.findByUsuarioEmail(usuario.getEmail())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estudiante> getById(@PathVariable Integer id,
                                              Authentication authentication,
                                              @AuthenticationPrincipal Usuario usuario) {
        return estudianteRepository.findById(id)
                .map(estudiante -> {
                    verificarPuedeVer(estudiante, authentication, usuario);
                    return ResponseEntity.ok(estudiante);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Estudiante create(@Valid @RequestBody Estudiante estudiante) {
        if (estudiante.getUsuario() == null || estudiante.getUsuario().getId() == null) {
            throw new IllegalArgumentException("Debes seleccionar la cuenta de usuario del estudiante.");
        }

        Integer usuarioId = estudiante.getUsuario().getId();
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La cuenta de usuario seleccionada no existe."));
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException(
                    "La cuenta de usuario seleccionada esta desactivada.");
        }
        boolean esEstudiante = usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(rol -> "ESTUDIANTE".equalsIgnoreCase(rol.getCodigo()));
        if (!esEstudiante) {
            throw new IllegalArgumentException(
                    "La cuenta seleccionada no tiene el rol ESTUDIANTE.");
        }
        if (estudianteRepository.findByUsuarioId(usuarioId).isPresent()) {
            throw new IllegalArgumentException("Ese usuario ya tiene un expediente de estudiante.");
        }

        estudiante.setUsuario(usuario);
        return estudianteRepository.save(estudiante);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estudiante> update(@PathVariable Integer id, @Valid @RequestBody Estudiante studentDetails,
                                             Authentication authentication) {
        return estudianteRepository.findById(id)
                .map(estudiante -> {
                    verificarPuedeGestionar(estudiante, authentication);
                    if (hasRole(authentication, "COORDINADOR")) {
                        verificarIdentidadInstitucionalSinCambios(estudiante, studentDetails);
                    } else {
                        estudiante.setMatricula(studentDetails.getMatricula());
                        estudiante.setCarrera(studentDetails.getCarrera());
                        if (studentDetails.getUsuario() != null) {
                            estudiante.setUsuario(studentDetails.getUsuario());
                        }
                    }
                    estudiante.setSemestre(studentDetails.getSemestre());
                    estudiante.setPeriodoAcademico(studentDetails.getPeriodoAcademico());
                    return ResponseEntity.ok(estudianteRepository.save(estudiante));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id,
                                       Authentication authentication) {
        // Defensa en profundidad: la matriz ya restringe DELETE a ADMIN
        if (!hasRole(authentication, "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo Administración puede eliminar estudiantes.");
        }
        return estudianteRepository.findById(id)
                .map(estudiante -> {
                    estudianteRepository.delete(estudiante);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private boolean carreraVisible(Authentication authentication, Estudiante estudiante) {
        if (!hasRole(authentication, "COORDINADOR")) return true;
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(carreras -> carreras.contains(estudiante.getCarrera()))
                .orElse(true);
    }

    private void verificarPuedeVer(Estudiante estudiante, Authentication authentication, Usuario usuario) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        if (hasRole(authentication, "TUTOR")
                && usuario != null
                && (practicaRepository.existsByEstudianteIdAndTutorId(estudiante.getId(), usuario.getId())
                    || vinculacionRepository.existsByEstudianteIdAndTutorId(estudiante.getId(), usuario.getId()))) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este estudiante.");
    }

    private void verificarPuedeGestionar(Estudiante estudiante, Authentication authentication) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "COORDINADOR") && carreraVisible(authentication, estudiante)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes gestionar este estudiante.");
    }

    private void verificarIdentidadInstitucionalSinCambios(Estudiante actual, Estudiante cambios) {
        boolean cambiaMatricula = cambios.getMatricula() != null
                && !Objects.equals(actual.getMatricula(), cambios.getMatricula());
        boolean cambiaCarrera = cambios.getCarrera() != null
                && !Objects.equals(actual.getCarrera(), cambios.getCarrera());
        boolean cambiaUsuario = cambios.getUsuario() != null
                && (actual.getUsuario() == null
                    || !Objects.equals(actual.getUsuario().getId(), cambios.getUsuario().getId()));
        if (cambiaMatricula || cambiaCarrera || cambiaUsuario) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo Administración puede cambiar matrícula, carrera o usuario del estudiante.");
        }
    }
}
