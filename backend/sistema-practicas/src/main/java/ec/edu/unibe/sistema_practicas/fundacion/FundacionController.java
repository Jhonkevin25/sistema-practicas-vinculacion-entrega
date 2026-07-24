package ec.edu.unibe.sistema_practicas.fundacion;

import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/fundaciones")
@RequiredArgsConstructor
public class FundacionController {

    private static final String PROCESO = "VINCULACION";

    private final FundacionRepository fundacionRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;

    @GetMapping
    public List<Fundacion> getAll(Authentication authentication) {
        Optional<Set<Integer>> visibles = idsVisibles(authentication);
        if (visibles.isPresent()) {
            return visibles.get().isEmpty()
                    ? List.of()
                    : fundacionRepository.findAllById(visibles.get());
        }
        return fundacionRepository.findAll();
    }

    // Fase 43: listado paginado con filtros (texto, activa) resuelto en la BD
    @GetMapping("/paginado")
    public PaginaResponse<Fundacion> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activa,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        Optional<Set<Integer>> visibles = idsVisibles(authentication);
        if (visibles.isPresent() && visibles.get().isEmpty()) {
            return PaginaResponse.deLista(List.of(), pagina, tamano);
        }
        Specification<Fundacion> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            visibles.ifPresent(ids -> condiciones.add(root.get("id").in(ids)));
            if (texto != null && !texto.isBlank()) {
                String like = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("ruc")), like)));
            }
            if (activa != null) {
                condiciones.add(cb.equal(root.get("activa"), activa));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
        String campoOrden = List.of("id", "nombre", "ruc", "activa").contains(sortBy) ? sortBy : "id";
        Sort sort = Sort.by(direction, campoOrden);

        return PaginaResponse.desde(fundacionRepository.findAll(spec,
                PaginaResponse.pageable(pagina, tamano, sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Fundacion> getById(@PathVariable Integer id, Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return fundacionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Fundacion create(@Valid @RequestBody Fundacion fundacion, Authentication authentication) {
        verificarProceso(authentication);
        return fundacionRepository.save(fundacion);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Fundacion> update(@PathVariable Integer id, @Valid @RequestBody Fundacion details,
                                            Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return fundacionRepository.findById(id)
                .map(fundacion -> {
                    fundacion.setRuc(details.getRuc());
                    fundacion.setNombre(details.getNombre());
                    fundacion.setMision(details.getMision());
                    fundacion.setAreaIntervencion(details.getAreaIntervencion());
                    fundacion.setActiva(details.getActiva());
                    fundacion.setTieneConvenio(details.getTieneConvenio());
                    return ResponseEntity.ok(fundacionRepository.save(fundacion));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return fundacionRepository.findById(id)
                .map(fundacion -> {
                    fundacionRepository.delete(fundacion);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void verificarProceso(Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no tiene acceso a fundaciones de vinculación.");
        }
    }

    private Optional<Set<Integer>> idsVisibles(Authentication authentication) {
        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(authentication);
        if (carreras.isEmpty()) return Optional.empty();
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return Optional.of(Set.of());
        }
        return Optional.of(convenioVigenteComponent.fundacionesVisiblesPara(carreras.get()));
    }

    private void verificarEntidadVisible(Integer fundacionId, Authentication authentication) {
        idsVisibles(authentication).ifPresent(ids -> {
            if (!ids.contains(fundacionId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La fundación no tiene un convenio vigente compatible con tu alcance académico.");
            }
        });
    }
}
