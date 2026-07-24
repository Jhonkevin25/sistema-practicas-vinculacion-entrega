package ec.edu.unibe.sistema_practicas.carrera;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carreras")
@RequiredArgsConstructor
public class CarreraController {

    private final CarreraRepository carreraRepository;

    @GetMapping
    public List<Carrera> getAll() {
        return carreraRepository.findAllByOrderByNombreAsc();
    }

    @PostMapping
    public Carrera create(@Valid @RequestBody Carrera carrera) {
        validarNombre(carrera, null);
        if (carrera.getActivo() == null) {
            carrera.setActivo(true);
        }
        return carreraRepository.save(carrera);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Carrera> update(@PathVariable Integer id, @RequestBody Carrera details) {
        return carreraRepository.findById(id)
                .map(existing -> {
                    if (details.getNombre() != null) {
                        validarNombre(details, id);
                        existing.setNombre(details.getNombre());
                    }
                    if (details.getActivo() != null) {
                        existing.setActivo(details.getActivo());
                    }
                    return ResponseEntity.ok(carreraRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return carreraRepository.findById(id)
                .map(carrera -> {
                    carreraRepository.delete(carrera);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void validarNombre(Carrera carrera, Integer idActual) {
        if (carrera.getNombre() == null || carrera.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la carrera es obligatorio.");
        }
        carrera.setNombre(carrera.getNombre().trim());
        carreraRepository.findByNombreIgnoreCase(carrera.getNombre())
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("Ya existe una carrera con ese nombre.");
                });
    }
}
