package ec.edu.unibe.sistema_practicas.configuracion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configuracion/fechas-limite")
@RequiredArgsConstructor
public class FechaLimiteCalificacionController {

    private final FechaLimiteCalificacionRepository fechaLimiteRepository;

    @GetMapping
    public List<FechaLimiteCalificacion> getAll(@RequestParam(required = false) String periodoAcademico) {
        if (periodoAcademico != null) {
            return fechaLimiteRepository.findByPeriodoAcademico(periodoAcademico);
        }
        return fechaLimiteRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FechaLimiteCalificacion> getById(@PathVariable Integer id) {
        return fechaLimiteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Upsert por (periodo, parcial)
    @PostMapping
    public FechaLimiteCalificacion upsert(@Valid @RequestBody FechaLimiteCalificacion fecha) {
        prepararYValidar(fecha, null);
        return fechaLimiteRepository
                .findByPeriodoAcademicoAndParcial(fecha.getPeriodoAcademico(), fecha.getParcial())
                .map(existing -> {
                    existing.setFechaLimite(fecha.getFechaLimite());
                    return fechaLimiteRepository.save(existing);
                })
                .orElseGet(() -> fechaLimiteRepository.save(fecha));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FechaLimiteCalificacion> update(@PathVariable Integer id,
                                                          @Valid @RequestBody FechaLimiteCalificacion details) {
        return fechaLimiteRepository.findById(id)
                .map(existing -> {
                    prepararYValidar(details, id);
                    existing.setPeriodoAcademico(details.getPeriodoAcademico());
                    existing.setParcial(details.getParcial());
                    existing.setFechaLimite(details.getFechaLimite());
                    return ResponseEntity.ok(fechaLimiteRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return fechaLimiteRepository.findById(id)
                .map(fecha -> {
                    fechaLimiteRepository.delete(fecha);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void prepararYValidar(FechaLimiteCalificacion fecha, Integer idActual) {
        if (fecha.getPeriodoAcademico() != null) {
            fecha.setPeriodoAcademico(fecha.getPeriodoAcademico().trim());
        }
        if (fecha.getPeriodoAcademico() == null || fecha.getPeriodoAcademico().isBlank()) {
            throw new IllegalArgumentException("El periodo académico es obligatorio.");
        }
        if (fecha.getParcial() == null || fecha.getParcial() < 1 || fecha.getParcial() > 3) {
            throw new IllegalArgumentException("El parcial debe ser 1, 2 o 3.");
        }
        if (fecha.getFechaLimite() == null) {
            throw new IllegalArgumentException("La fecha límite es obligatoria.");
        }
        validarOrdenParciales(fecha, idActual);
    }

    private void validarOrdenParciales(FechaLimiteCalificacion candidata, Integer idActual) {
        Map<Integer, LocalDate> fechas = new HashMap<>();
        fechaLimiteRepository.findByPeriodoAcademico(candidata.getPeriodoAcademico()).stream()
                .filter(f -> idActual == null || !idActual.equals(f.getId()))
                .forEach(f -> fechas.put(f.getParcial(), f.getFechaLimite()));
        fechas.put(candidata.getParcial(), candidata.getFechaLimite());

        LocalDate parcial1 = fechas.get(1);
        LocalDate parcial2 = fechas.get(2);
        LocalDate parcial3 = fechas.get(3);
        if (parcial1 != null && parcial2 != null && parcial2.isBefore(parcial1)) {
            throw new IllegalArgumentException("La fecha límite del Parcial 2 no puede ser anterior a la del Parcial 1.");
        }
        if (parcial2 != null && parcial3 != null && parcial3.isBefore(parcial2)) {
            throw new IllegalArgumentException("La fecha límite del Parcial 3 no puede ser anterior a la del Parcial 2.");
        }
    }
}
