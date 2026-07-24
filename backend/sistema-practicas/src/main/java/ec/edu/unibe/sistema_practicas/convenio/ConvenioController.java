package ec.edu.unibe.sistema_practicas.convenio;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/convenios")
@RequiredArgsConstructor
public class ConvenioController {

    private static final List<String> ESTADOS = List.of("VIGENTE", "SUSPENDIDO", "FINALIZADO");

    private final ConvenioRepository convenioRepository;
    private final EmpresaRepository empresaRepository;
    private final FundacionRepository fundacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;

    @GetMapping
    public List<Convenio> getAll(Authentication authentication) {
        VisibilidadEntidades visibilidad = visibilidad(authentication);
        return convenioRepository.findAll().stream()
                .filter(convenio -> visible(visibilidad, convenio))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Convenio> getById(@PathVariable Integer id, Authentication authentication) {
        VisibilidadEntidades visibilidad = visibilidad(authentication);
        return convenioRepository.findById(id)
                .filter(convenio -> visible(visibilidad, convenio))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/empresa/{empresaId}")
    public List<Convenio> getByEmpresa(@PathVariable Integer empresaId, Authentication authentication) {
        VisibilidadEntidades visibilidad = visibilidad(authentication);
        if (visibilidad.restringida() && !visibilidad.empresas().contains(empresaId)) return List.of();
        return convenioRepository.findByEmpresaIdOrderByFechaFinDesc(empresaId);
    }

    @GetMapping("/fundacion/{fundacionId}")
    public List<Convenio> getByFundacion(@PathVariable Integer fundacionId, Authentication authentication) {
        VisibilidadEntidades visibilidad = visibilidad(authentication);
        if (visibilidad.restringida() && !visibilidad.fundaciones().contains(fundacionId)) return List.of();
        return convenioRepository.findByFundacionIdOrderByFechaFinDesc(fundacionId);
    }

    @PostMapping
    public Convenio create(@Valid @RequestBody Convenio convenio) {
        normalizarYValidar(convenio, null);
        return convenioRepository.save(convenio);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Convenio> update(@PathVariable Integer id,
                                           @Valid @RequestBody Convenio details) {
        return convenioRepository.findById(id)
                .map(convenio -> {
                    normalizarYValidar(details, id);
                    convenio.setCodigo(details.getCodigo());
                    convenio.setEmpresa(details.getEmpresa());
                    convenio.setFundacion(details.getFundacion());
                    convenio.setFechaInicio(details.getFechaInicio());
                    convenio.setFechaFin(details.getFechaFin());
                    convenio.setEstado(details.getEstado());
                    convenio.setUrlDocumento(details.getUrlDocumento());
                    convenio.setCuposPactados(details.getCuposPactados());
                    convenio.setCarreras(details.getCarreras());
                    return ResponseEntity.ok(convenioRepository.save(convenio));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return convenioRepository.findById(id)
                .map(convenio -> {
                    convenioRepository.delete(convenio);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void normalizarYValidar(Convenio convenio, Integer convenioId) {
        boolean tieneEmpresa = convenio.getEmpresa() != null && convenio.getEmpresa().getId() != null;
        boolean tieneFundacion = convenio.getFundacion() != null && convenio.getFundacion().getId() != null;
        if (tieneEmpresa == tieneFundacion) {
            throw new IllegalArgumentException("El convenio debe pertenecer a una empresa o a una fundación, no a ambas.");
        }
        if (convenio.getFechaInicio() == null || convenio.getFechaFin() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin del convenio son obligatorias.");
        }
        if (convenio.getFechaInicio().isAfter(convenio.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio del convenio no puede ser posterior a la fecha de fin.");
        }
        int cupos = convenio.getCuposPactados() == null ? 0 : convenio.getCuposPactados();
        if (cupos < 0) {
            throw new IllegalArgumentException("Los cupos pactados no pueden ser negativos.");
        }

        String codigo = convenio.getCodigo() == null ? "" : convenio.getCodigo().trim().toUpperCase(Locale.ROOT);
        if (codigo.isBlank()) {
            throw new IllegalArgumentException("El código del convenio es obligatorio.");
        }
        boolean codigoDuplicado = convenioId == null
                ? convenioRepository.existsByCodigoIgnoreCase(codigo)
                : convenioRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, convenioId);
        if (codigoDuplicado) {
            throw new IllegalArgumentException("Ya existe un convenio con ese código.");
        }

        String estado = convenio.getEstado() == null
                ? "VIGENTE"
                : convenio.getEstado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(estado)) {
            throw new IllegalArgumentException("El estado del convenio no es válido.");
        }

        convenio.setCodigo(codigo);
        convenio.setEstado(estado);
        convenio.setCuposPactados(cupos);
        convenio.setUrlDocumento(convenio.getUrlDocumento() == null
                || convenio.getUrlDocumento().isBlank() ? null : convenio.getUrlDocumento().trim());
        convenio.setCarreras(normalizarCarreras(convenio.getCarreras()));
        if (tieneEmpresa) {
            convenio.setEmpresa(empresaRepository.findById(convenio.getEmpresa().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada.")));
            convenio.setFundacion(null);
        } else {
            convenio.setFundacion(fundacionRepository.findById(convenio.getFundacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundación no encontrada.")));
            convenio.setEmpresa(null);
        }
    }

    private LinkedHashSet<String> normalizarCarreras(Iterable<String> carreras) {
        LinkedHashSet<String> normalizadas = new LinkedHashSet<>();
        if (carreras == null) {
            return normalizadas;
        }
        for (String carrera : carreras) {
            if (carrera != null && !carrera.isBlank()) {
                normalizadas.add(carrera.trim());
            }
        }
        return normalizadas;
    }

    private boolean visible(VisibilidadEntidades visibilidad, Convenio convenio) {
        if (!visibilidad.restringida()) return true;
        if (convenio.getEmpresa() != null) {
            return visibilidad.empresas().contains(convenio.getEmpresa().getId());
        }
        if (convenio.getFundacion() != null) {
            return visibilidad.fundaciones().contains(convenio.getFundacion().getId());
        }
        return false;
    }

    private VisibilidadEntidades visibilidad(Authentication authentication) {
        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(authentication);
        if (carreras.isEmpty()) {
            return new VisibilidadEntidades(false, Set.of(), Set.of());
        }
        Set<Integer> empresas = alcanceCoordinador.procesoVisible(authentication, "PRACTICAS")
                ? convenioVigenteComponent.empresasVisiblesPara(carreras.get())
                : Set.of();
        Set<Integer> fundaciones = alcanceCoordinador.procesoVisible(authentication, "VINCULACION")
                ? convenioVigenteComponent.fundacionesVisiblesPara(carreras.get())
                : Set.of();
        return new VisibilidadEntidades(true, empresas, fundaciones);
    }

    private record VisibilidadEntidades(
            boolean restringida,
            Set<Integer> empresas,
            Set<Integer> fundaciones) {
    }
}
