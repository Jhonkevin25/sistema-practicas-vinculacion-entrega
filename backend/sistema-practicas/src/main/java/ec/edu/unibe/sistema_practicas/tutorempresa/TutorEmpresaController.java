package ec.edu.unibe.sistema_practicas.tutorempresa;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
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
@RequestMapping("/api/tutores-empresa")
@RequiredArgsConstructor
public class TutorEmpresaController {

    private static final String PROCESO = "PRACTICAS";

    private final TutorEmpresaRepository tutorEmpresaRepository;
    private final EmpresaRepository empresaRepository;
    private final CarreraRepository carreraRepository;
    private final TutorAsignacionComponent tutorAsignacionComponent;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;

    @GetMapping("/empresa/{empresaId}")
    public List<TutorEmpresaResponse> porEmpresa(@PathVariable Integer empresaId,
                                                 Authentication authentication) {
        Empresa empresa = exigirEmpresa(empresaId);
        exigirProcesoVisible(authentication);
        Set<String> carrerasVisibles = carrerasVisibles(authentication);
        exigirEmpresaVisible(empresa.getId(), carrerasVisibles);
        return tutorEmpresaRepository.findByEmpresaIdOrderByNombreAsc(empresa.getId()).stream()
                .filter(vinculo -> carrerasVisibles == null || vinculo.getCarreras().stream()
                        .anyMatch(carrera -> contieneCarrera(carrerasVisibles, carrera.getNombre())))
                .map(this::respuesta)
                .toList();
    }

    @GetMapping("/compatibles")
    public List<TutorEmpresaResponse> compatibles(@RequestParam Integer empresaId,
                                                   @RequestParam String carrera,
                                                   Authentication authentication) {
        Empresa empresa = exigirEmpresa(empresaId);
        String carreraNormalizada = exigirCarrera(carrera);
        exigirProcesoVisible(authentication);
        Set<String> carrerasVisibles = carrerasVisibles(authentication);
        exigirEmpresaVisible(empresa.getId(), carrerasVisibles);
        if (carrerasVisibles != null && !contieneCarrera(carrerasVisibles, carreraNormalizada)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La carrera indicada no pertenece al alcance del coordinador.");
        }
        return tutorEmpresaRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresa.getId()).stream()
                .filter(this::cuentaTutorActiva)
                .filter(vinculo -> cubreCarrera(vinculo, carreraNormalizada))
                .map(this::respuesta)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TutorEmpresaResponse crear(@RequestBody TutorEmpresaRequest request) {
        validarRequest(request);
        Usuario tutor = tutorAsignacionComponent.exigirValido(
                referenciaUsuario(request.tutorId()), PROCESO);
        Empresa empresa = exigirEmpresa(request.empresaId());
        if (tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(tutor.getId(), empresa.getId()).isPresent()) {
            throw new IllegalArgumentException("El tutor ya está vinculado con esta empresa.");
        }
        TutorEmpresa vinculo = new TutorEmpresa();
        aplicar(vinculo, tutor, empresa, request);
        return respuesta(tutorEmpresaRepository.save(vinculo));
    }

    @PutMapping("/{id}")
    public TutorEmpresaResponse actualizar(@PathVariable Integer id,
                                            @RequestBody TutorEmpresaRequest request) {
        TutorEmpresa vinculo = tutorEmpresaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vínculo de tutor no encontrado."));
        validarRequest(request);
        Usuario tutor = tutorAsignacionComponent.exigirValido(
                referenciaUsuario(request.tutorId()), PROCESO);
        Empresa empresa = exigirEmpresa(request.empresaId());
        tutorEmpresaRepository.findByUsuarioIdAndEmpresaId(tutor.getId(), empresa.getId())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("El tutor ya está vinculado con esta empresa.");
                });
        aplicar(vinculo, tutor, empresa, request);
        return respuesta(tutorEmpresaRepository.save(vinculo));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        TutorEmpresa vinculo = tutorEmpresaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vínculo de tutor no encontrado."));
        tutorEmpresaRepository.delete(vinculo);
    }

    private void aplicar(TutorEmpresa vinculo, Usuario tutor, Empresa empresa, TutorEmpresaRequest request) {
        vinculo.setUsuario(tutor);
        vinculo.setEmpresa(empresa);
        vinculo.setNombre(nombreCompleto(tutor));
        vinculo.setCargo(limpiar(request.cargo()));
        vinculo.setActivo(request.activo() == null || request.activo());
        vinculo.setCarreras(new LinkedHashSet<>(carreras(request.carreraIds())));
        vinculo.setUpdatedAt(LocalDateTime.now());
    }

    private void validarRequest(TutorEmpresaRequest request) {
        if (request == null || request.tutorId() == null || request.empresaId() == null) {
            throw new IllegalArgumentException("Debes indicar el tutor y la empresa.");
        }
        if (request.carreraIds() == null || request.carreraIds().isEmpty()) {
            throw new IllegalArgumentException("El tutor debe cubrir al menos una carrera en la empresa.");
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

    private Empresa exigirEmpresa(Integer empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada."));
        if (!Boolean.TRUE.equals(empresa.getActivo())) {
            throw new IllegalArgumentException("La empresa debe estar activa para administrar tutores.");
        }
        return empresa;
    }

    private Usuario referenciaUsuario(Integer tutorId) {
        Usuario referencia = new Usuario();
        referencia.setId(tutorId);
        return referencia;
    }

    private boolean cuentaTutorActiva(TutorEmpresa vinculo) {
        if (vinculo.getUsuario() == null || !Boolean.TRUE.equals(vinculo.getUsuario().getActivo())) return false;
        return vinculo.getUsuario().getRoles() != null && vinculo.getUsuario().getRoles().stream()
                .anyMatch(rol -> "TUTOR".equalsIgnoreCase(rol.getCodigo()));
    }

    private boolean cubreCarrera(TutorEmpresa vinculo, String carrera) {
        return vinculo.getCarreras().stream().anyMatch(item -> mismaCarrera(item.getNombre(), carrera));
    }

    private Set<String> carrerasVisibles(Authentication authentication) {
        if (tieneRol(authentication, "ADMIN")) return null;
        return alcanceCoordinador.carrerasVisibles(authentication).orElse(Set.of());
    }

    private void exigirProcesoVisible(Authentication authentication) {
        if (!tieneRol(authentication, "ADMIN") && !alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El usuario no tiene alcance para administrar tutores de prácticas.");
        }
    }

    private void exigirEmpresaVisible(Integer empresaId, Set<String> carrerasVisibles) {
        if (carrerasVisibles != null
                && !convenioVigenteComponent.empresasVisiblesPara(carrerasVisibles).contains(empresaId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La empresa no tiene un convenio vigente compatible con el alcance del coordinador.");
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

    private TutorEmpresaResponse respuesta(TutorEmpresa vinculo) {
        Usuario tutor = vinculo.getUsuario();
        Empresa empresa = vinculo.getEmpresa();
        return new TutorEmpresaResponse(
                vinculo.getId(),
                tutor == null ? null : tutor.getId(),
                tutor == null ? vinculo.getNombre() : tutor.getNombre(),
                tutor == null ? "" : tutor.getApellido(),
                tutor == null ? null : tutor.getEmail(),
                empresa == null ? null : empresa.getId(),
                empresa == null ? null : empresa.getNombre(),
                vinculo.getCargo(),
                vinculo.getActivo(),
                vinculo.getCarreras().stream()
                        .map(carrera -> new TutorEmpresaResponse.CarreraResumen(carrera.getId(), carrera.getNombre()))
                        .toList()
        );
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + rol).equals(authority.getAuthority()));
    }
}
