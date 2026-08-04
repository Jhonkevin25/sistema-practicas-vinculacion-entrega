package ec.edu.unibe.sistema_practicas.tutorfundacion;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.usuario.TutorAsignacionComponent;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/tutores-fundacion")
@RequiredArgsConstructor
public class TutorFundacionController {

    private static final String PROCESO = "VINCULACION";

    private final TutorFundacionRepository tutorFundacionRepository;
    private final FundacionRepository fundacionRepository;
    private final CarreraRepository carreraRepository;
    private final TutorAsignacionComponent tutorAsignacionComponent;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;

    @GetMapping("/fundacion/{fundacionId}")
    public List<TutorFundacionResponse> porFundacion(@PathVariable Integer fundacionId,
                                                      Authentication authentication) {
        Fundacion fundacion = exigirFundacion(fundacionId);
        exigirProcesoVisible(authentication);
        Set<String> carrerasVisibles = carrerasVisibles(authentication);
        exigirFundacionVisible(fundacion.getId(), carrerasVisibles);
        return tutorFundacionRepository.findByFundacionIdOrderByNombreAsc(fundacion.getId()).stream()
                .filter(vinculo -> carrerasVisibles == null || vinculo.getCarreras().stream()
                        .anyMatch(carrera -> contieneCarrera(carrerasVisibles, carrera.getNombre())))
                .map(this::respuesta)
                .toList();
    }

    @GetMapping("/compatibles")
    public List<TutorFundacionResponse> compatibles(@RequestParam Integer fundacionId,
                                                      @RequestParam String carrera,
                                                      Authentication authentication) {
        Fundacion fundacion = exigirFundacion(fundacionId);
        String carreraNormalizada = exigirCarrera(carrera);
        exigirProcesoVisible(authentication);
        Set<String> carrerasVisibles = carrerasVisibles(authentication);
        exigirFundacionVisible(fundacion.getId(), carrerasVisibles);
        if (carrerasVisibles != null && !contieneCarrera(carrerasVisibles, carreraNormalizada)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La carrera indicada no pertenece al alcance del coordinador.");
        }
        return tutorFundacionRepository.findByFundacionIdAndActivoTrueOrderByNombreAsc(fundacion.getId()).stream()
                .filter(this::cuentaTutorActiva)
                .filter(vinculo -> cubreCarrera(vinculo, carreraNormalizada))
                .map(this::respuesta)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TutorFundacionResponse crear(@RequestBody TutorFundacionRequest request) {
        validarRequest(request);
        Usuario tutor = tutorAsignacionComponent.exigirValido(
                referenciaUsuario(request.tutorId()), PROCESO);
        Fundacion fundacion = exigirFundacion(request.fundacionId());
        if (tutorFundacionRepository.findByUsuarioIdAndFundacionId(tutor.getId(), fundacion.getId()).isPresent()) {
            throw new IllegalArgumentException("El tutor ya está vinculado con esta fundación.");
        }
        TutorFundacion vinculo = new TutorFundacion();
        aplicar(vinculo, tutor, fundacion, request);
        return respuesta(tutorFundacionRepository.save(vinculo));
    }

    @PutMapping("/{id}")
    public TutorFundacionResponse actualizar(@PathVariable Integer id,
                                              @RequestBody TutorFundacionRequest request) {
        TutorFundacion vinculo = tutorFundacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vínculo de tutor no encontrado."));
        validarRequest(request);
        Usuario tutor = tutorAsignacionComponent.exigirValido(
                referenciaUsuario(request.tutorId()), PROCESO);
        Fundacion fundacion = exigirFundacion(request.fundacionId());
        tutorFundacionRepository.findByUsuarioIdAndFundacionId(tutor.getId(), fundacion.getId())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("El tutor ya está vinculado con esta fundación.");
                });
        aplicar(vinculo, tutor, fundacion, request);
        return respuesta(tutorFundacionRepository.save(vinculo));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        TutorFundacion vinculo = tutorFundacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vínculo de tutor no encontrado."));
        tutorFundacionRepository.delete(vinculo);
    }

    private void aplicar(TutorFundacion vinculo, Usuario tutor, Fundacion fundacion, TutorFundacionRequest request) {
        vinculo.setUsuario(tutor);
        vinculo.setFundacion(fundacion);
        vinculo.setNombre(nombreCompleto(tutor));
        vinculo.setCargo(limpiar(request.cargo()));
        vinculo.setActivo(request.activo() == null || request.activo());
        vinculo.setCarreras(new LinkedHashSet<>(carreras(request.carreraIds())));
        vinculo.setUpdatedAt(LocalDateTime.now());
    }

    private void validarRequest(TutorFundacionRequest request) {
        if (request == null || request.tutorId() == null || request.fundacionId() == null) {
            throw new IllegalArgumentException("Debes indicar el tutor y la fundación.");
        }
        if (request.carreraIds() == null || request.carreraIds().isEmpty()) {
            throw new IllegalArgumentException("El tutor debe cubrir al menos una carrera en la fundación.");
        }
        if (request.cargo() != null && request.cargo().trim().length() > 100) {
            throw new IllegalArgumentException("El cargo no puede superar 100 caracteres.");
        }
    }

    private Set<Carrera> carreras(Set<Integer> carreraIds) {
        Set<Carrera> carreras = new LinkedHashSet<>(carreraRepository.findAllById(carreraIds));
        if (carreras.size() != carreraIds.size()) {
            throw new IllegalArgumentException("Una o más carreras seleccionadas no existen.");
        }
        if (carreras.stream().anyMatch(carrera -> !Boolean.TRUE.equals(carrera.getActivo()))) {
            throw new IllegalArgumentException("No se puede asignar una carrera inactiva al tutor.");
        }
        return carreras;
    }

    private Fundacion exigirFundacion(Integer fundacionId) {
        Fundacion fundacion = fundacionRepository.findById(fundacionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundación no encontrada."));
        if (!Boolean.TRUE.equals(fundacion.getActiva())) {
            throw new IllegalArgumentException("La fundación debe estar activa para administrar tutores.");
        }
        return fundacion;
    }

    private Usuario referenciaUsuario(Integer tutorId) {
        Usuario referencia = new Usuario();
        referencia.setId(tutorId);
        return referencia;
    }

    private boolean cuentaTutorActiva(TutorFundacion vinculo) {
        if (vinculo.getUsuario() == null || !Boolean.TRUE.equals(vinculo.getUsuario().getActivo())) return false;
        return vinculo.getUsuario().getRoles() != null && vinculo.getUsuario().getRoles().stream()
                .anyMatch(rol -> "TUTOR".equalsIgnoreCase(rol.getCodigo()));
    }

    private boolean cubreCarrera(TutorFundacion vinculo, String carrera) {
        return vinculo.getCarreras().stream().anyMatch(item -> mismaCarrera(item.getNombre(), carrera));
    }

    private Set<String> carrerasVisibles(Authentication authentication) {
        if (tieneRol(authentication, "ADMIN")) return null;
        return alcanceCoordinador.carrerasVisibles(authentication).orElse(Set.of());
    }

    private void exigirProcesoVisible(Authentication authentication) {
        if (!tieneRol(authentication, "ADMIN") && !alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El usuario no tiene alcance para administrar tutores de vinculación.");
        }
    }

    private void exigirFundacionVisible(Integer fundacionId, Set<String> carrerasVisibles) {
        if (carrerasVisibles != null
                && !convenioVigenteComponent.fundacionesVisiblesPara(carrerasVisibles).contains(fundacionId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La fundación no tiene un convenio vigente compatible con el alcance del coordinador.");
        }
    }

    private boolean contieneCarrera(Set<String> carreras, String carrera) {
        return carreras.stream().anyMatch(item -> mismaCarrera(item, carrera));
    }

    private boolean mismaCarrera(String izquierda, String derecha) {
        return normalizar(izquierda).equals(normalizar(derecha));
    }

    private String exigirCarrera(String carrera) {
        String valor = limpiar(carrera);
        if (valor == null) throw new IllegalArgumentException("Debes indicar la carrera de la vacante.");
        return valor;
    }

    private String normalizar(String valor) {
        if (valor == null) return "";
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String limpiar(String valor) {
        if (valor == null || valor.trim().isEmpty()) return null;
        return valor.trim();
    }

    private String nombreCompleto(Usuario usuario) {
        return ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
                + (usuario.getApellido() == null ? "" : usuario.getApellido())).trim();
    }

    private TutorFundacionResponse respuesta(TutorFundacion vinculo) {
        Usuario tutor = vinculo.getUsuario();
        Fundacion fundacion = vinculo.getFundacion();
        return new TutorFundacionResponse(
                vinculo.getId(),
                tutor == null ? null : tutor.getId(),
                tutor == null ? vinculo.getNombre() : tutor.getNombre(),
                tutor == null ? "" : tutor.getApellido(),
                tutor == null ? null : tutor.getEmail(),
                fundacion == null ? null : fundacion.getId(),
                fundacion == null ? null : fundacion.getNombre(),
                vinculo.getCargo(),
                vinculo.getActivo(),
                vinculo.getCarreras().stream()
                        .map(carrera -> new TutorFundacionResponse.CarreraResumen(carrera.getId(), carrera.getNombre()))
                        .toList()
        );
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + rol).equals(authority.getAuthority()));
    }
}
