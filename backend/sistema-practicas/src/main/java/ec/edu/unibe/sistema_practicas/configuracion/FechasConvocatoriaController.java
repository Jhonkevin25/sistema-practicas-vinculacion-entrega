package ec.edu.unibe.sistema_practicas.configuracion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/configuracion/fechas")
@RequiredArgsConstructor
public class FechasConvocatoriaController {

    private static final Set<String> TIPOS_VALIDOS = Set.of("PRACTICAS", "VINCULACION");

    private final FechasConvocatoriaRepository fechasConvocatoriaRepository;

    @GetMapping
    public List<FechasConvocatoria> getAll(@RequestParam(required = false) String periodoAcademico,
                                           @RequestParam(required = false) String tipo) {
        if (periodoAcademico != null && tipo != null) {
            return fechasConvocatoriaRepository
                    .findByPeriodoAcademicoAndTipo(periodoAcademico, normalizarTipo(tipo))
                    .map(List::of)
                    .orElse(List.of());
        }
        if (periodoAcademico != null) {
            return fechasConvocatoriaRepository.findByPeriodoAcademico(periodoAcademico);
        }
        if (tipo != null) {
            return fechasConvocatoriaRepository.findByTipo(normalizarTipo(tipo));
        }
        return fechasConvocatoriaRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FechasConvocatoria> getById(@PathVariable Integer id) {
        return fechasConvocatoriaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Upsert: la tabla tiene UNIQUE(periodo_academico, tipo); si ya existe
    // la convocatoria del periodo se actualizan sus fechas.
    @PostMapping
    public FechasConvocatoria create(@Valid @RequestBody FechasConvocatoria fechas) {
        prepararYValidar(fechas);
        return fechasConvocatoriaRepository
                .findByPeriodoAcademicoAndTipo(fechas.getPeriodoAcademico(), fechas.getTipo())
                .map(existing -> {
                    copiarFechas(existing, fechas);
                    return fechasConvocatoriaRepository.save(existing);
                })
                .orElseGet(() -> fechasConvocatoriaRepository.save(fechas));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FechasConvocatoria> update(@PathVariable Integer id,
                                                     @Valid @RequestBody FechasConvocatoria details) {
        prepararYValidar(details);
        return fechasConvocatoriaRepository.findById(id)
                .map(existing -> {
                    fechasConvocatoriaRepository
                            .findByPeriodoAcademicoAndTipo(details.getPeriodoAcademico(), details.getTipo())
                            .filter(duplicada -> !duplicada.getId().equals(id))
                            .ifPresent(duplicada -> {
                                throw new IllegalArgumentException(
                                        "Ya existe una configuración para ese periodo y tipo de proceso.");
                            });
                    existing.setPeriodoAcademico(details.getPeriodoAcademico());
                    existing.setTipo(details.getTipo());
                    copiarFechas(existing, details);
                    return ResponseEntity.ok(fechasConvocatoriaRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return fechasConvocatoriaRepository.findById(id)
                .map(fechas -> {
                    fechasConvocatoriaRepository.delete(fechas);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void copiarFechas(FechasConvocatoria target, FechasConvocatoria source) {
        target.setConvocatoriaInicio(source.getConvocatoriaInicio());
        target.setConvocatoriaFin(source.getConvocatoriaFin());
        target.setFechaLimiteDocumentos(source.getFechaLimiteDocumentos());
        target.setFechaInicioPostulacion(source.getFechaInicioPostulacion());
        target.setCreadoPor(source.getCreadoPor());
    }

    private void prepararYValidar(FechasConvocatoria fechas) {
        if (fechas.getPeriodoAcademico() != null) {
            fechas.setPeriodoAcademico(fechas.getPeriodoAcademico().trim());
        }
        fechas.setTipo(normalizarTipo(fechas.getTipo()));
        if (fechas.getPeriodoAcademico() == null || fechas.getPeriodoAcademico().isBlank()) {
            throw new IllegalArgumentException("El periodo académico es obligatorio.");
        }
        validarOrden(fechas);
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo de proceso es obligatorio.");
        }
        String normalizado = tipo.trim().toUpperCase();
        if (!TIPOS_VALIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("El tipo debe ser PRACTICAS o VINCULACION.");
        }
        return normalizado;
    }

    private void validarOrden(FechasConvocatoria fechas) {
        LocalDate inicio = fechas.getConvocatoriaInicio();
        LocalDate documentos = fechas.getFechaLimiteDocumentos();
        LocalDate postulacion = fechas.getFechaInicioPostulacion();
        LocalDate cierre = fechas.getConvocatoriaFin();

        if (inicio == null || documentos == null || postulacion == null || cierre == null) {
            throw new IllegalArgumentException(
                    "Debes configurar inicio de convocatoria, límite de documentos, inicio de postulación y cierre.");
        }
        if (documentos.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha límite de documentos no puede ser anterior al inicio de convocatoria.");
        }
        if (postulacion.isBefore(documentos)) {
            throw new IllegalArgumentException("El inicio de postulación no puede ser anterior al límite de documentos.");
        }
        if (cierre.isBefore(postulacion)) {
            throw new IllegalArgumentException("El cierre de convocatoria no puede ser anterior al inicio de postulación.");
        }
    }
}
