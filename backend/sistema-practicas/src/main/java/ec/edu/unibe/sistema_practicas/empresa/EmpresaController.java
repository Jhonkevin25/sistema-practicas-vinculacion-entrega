package ec.edu.unibe.sistema_practicas.empresa;

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
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private static final String PROCESO = "PRACTICAS";

    private final EmpresaRepository empresaRepository;
    private final AlcanceCoordinador alcanceCoordinador;
    private final ConvenioVigenteComponent convenioVigenteComponent;

    @GetMapping
    public List<Empresa> getAll(Authentication authentication) {
        Optional<Set<Integer>> visibles = idsVisibles(authentication);
        if (visibles.isPresent()) {
            return visibles.get().isEmpty()
                    ? List.of()
                    : empresaRepository.findAllById(visibles.get());
        }
        return empresaRepository.findAll();
    }

    // Fase 43: listado paginado con filtros (texto, activo) resuelto en la BD
    @GetMapping("/paginado")
    public PaginaResponse<Empresa> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        Optional<Set<Integer>> visibles = idsVisibles(authentication);
        if (visibles.isPresent() && visibles.get().isEmpty()) {
            return PaginaResponse.deLista(List.of(), pagina, tamano);
        }
        Specification<Empresa> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            visibles.ifPresent(ids -> condiciones.add(root.get("id").in(ids)));
            if (texto != null && !texto.isBlank()) {
                String like = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("ruc")), like)));
            }
            if (activo != null) {
                condiciones.add(cb.equal(root.get("activo"), activo));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
        String campoOrden = List.of("id", "nombre", "ruc", "activo").contains(sortBy) ? sortBy : "id";
        Sort sort = Sort.by(direction, campoOrden);

        return PaginaResponse.desde(empresaRepository.findAll(spec,
                PaginaResponse.pageable(pagina, tamano, sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Empresa> getById(@PathVariable Integer id, Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return empresaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Empresa create(@Valid @RequestBody Empresa empresa, Authentication authentication) {
        verificarProceso(authentication);
        empresa.setCuposDisponibles(0);
        return empresaRepository.save(empresa);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Empresa> update(@PathVariable Integer id, @Valid @RequestBody Empresa companyDetails,
                                          Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return empresaRepository.findById(id)
                .map(empresa -> {
                    empresa.setRuc(companyDetails.getRuc());
                    empresa.setNombre(companyDetails.getNombre());
                    empresa.setDireccion(companyDetails.getDireccion());
                    empresa.setActivo(companyDetails.getActivo());
                    empresa.setTieneConvenio(companyDetails.getTieneConvenio());
                    return ResponseEntity.ok(empresaRepository.save(empresa));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        verificarEntidadVisible(id, authentication);
        return empresaRepository.findById(id)
                .map(empresa -> {
                    empresaRepository.delete(empresa);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void verificarProceso(Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no tiene acceso a empresas de prácticas.");
        }
    }

    private Optional<Set<Integer>> idsVisibles(Authentication authentication) {
        Optional<Set<String>> carreras = alcanceCoordinador.carrerasVisibles(authentication);
        if (carreras.isEmpty()) return Optional.empty();
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            return Optional.of(Set.of());
        }
        return Optional.of(convenioVigenteComponent.empresasVisiblesPara(carreras.get()));
    }

    private void verificarEntidadVisible(Integer empresaId, Authentication authentication) {
        idsVisibles(authentication).ifPresent(ids -> {
            if (!ids.contains(empresaId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La empresa no tiene un convenio vigente compatible con tu alcance académico.");
            }
        });
    }
}
