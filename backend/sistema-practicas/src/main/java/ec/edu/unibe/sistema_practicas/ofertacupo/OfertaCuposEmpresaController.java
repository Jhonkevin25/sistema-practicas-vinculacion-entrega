package ec.edu.unibe.sistema_practicas.ofertacupo;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.configuracion.FechasConvocatoriaRepository;
import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.empresa.EmpresaRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/ofertas-cupos-empresa")
@RequiredArgsConstructor
public class OfertaCuposEmpresaController {

    private static final String PROCESO = "PRACTICAS";
    private static final Set<String> DISTRIBUCIONES = Set.of("GENERAL", "POR_CARRERA");

    private final OfertaCuposEmpresaRepository ofertaRepository;
    private final OfertaCuposEmpresaComponent ofertaComponent;
    private final EmpresaRepository empresaRepository;
    private final CarreraRepository carreraRepository;
    private final FechasConvocatoriaRepository fechasConvocatoriaRepository;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final AlcanceCoordinador alcanceCoordinador;

    @GetMapping
    public List<OfertaCuposEmpresa> getAll(
            @RequestParam(required = false) String periodoAcademico,
            Authentication authentication) {
        verificarProceso(authentication);
        List<OfertaCuposEmpresa> ofertas = periodoAcademico == null || periodoAcademico.isBlank()
                ? ofertaRepository.findAll()
                : ofertaRepository.findByPeriodoAcademico(periodoAcademico.trim());
        Optional<Set<Integer>> empresasVisibles = empresasVisibles(authentication);
        return ofertas.stream()
                .filter(oferta -> empresasVisibles.isEmpty()
                        || empresasVisibles.get().contains(oferta.getEmpresa().getId()))
                .map(ofertaComponent::enriquecer)
                .sorted((a, b) -> a.getEmpresa().getNombre().compareToIgnoreCase(b.getEmpresa().getNombre()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfertaCuposEmpresa> getById(
            @PathVariable Integer id,
            Authentication authentication) {
        verificarProceso(authentication);
        return ofertaRepository.findById(id)
                .map(oferta -> {
                    verificarOfertaVisible(oferta, authentication);
                    return oferta;
                })
                .map(ofertaComponent::enriquecer)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public OfertaCuposEmpresa create(@Valid @RequestBody OfertaCuposEmpresa details,
                                     Authentication authentication) {
        verificarProceso(authentication);
        OfertaCuposEmpresa oferta = new OfertaCuposEmpresa();
        normalizar(oferta, details, false);
        if (ofertaRepository.existsByEmpresaIdAndPeriodoAcademico(
                oferta.getEmpresa().getId(), oferta.getPeriodoAcademico())) {
            throw new IllegalArgumentException("La empresa ya tiene una oferta de cupos para este periodo.");
        }
        ofertaComponent.validarConfiguracion(oferta);
        return ofertaComponent.enriquecer(ofertaRepository.save(oferta));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<OfertaCuposEmpresa> update(
            @PathVariable Integer id,
            @Valid @RequestBody OfertaCuposEmpresa details,
            Authentication authentication) {
        verificarProceso(authentication);
        return ofertaRepository.findByIdForUpdate(id)
                .map(oferta -> {
                    normalizar(oferta, details, true);
                    ofertaComponent.validarConfiguracion(oferta);
                    return ResponseEntity.ok(ofertaComponent.enriquecer(ofertaRepository.save(oferta)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication authentication) {
        verificarProceso(authentication);
        return ofertaRepository.findByIdForUpdate(id)
                .map(oferta -> {
                    int reservados = ofertaComponent.cuposReservados(
                            oferta.getEmpresa().getId(), oferta.getPeriodoAcademico());
                    int ocupados = ofertaComponent.cuposOcupados(
                            oferta.getEmpresa().getId(), oferta.getPeriodoAcademico());
                    if (reservados + ocupados > 0) {
                        throw new IllegalArgumentException(
                                "No se puede eliminar una oferta con cupos reservados u ocupados.");
                    }
                    ofertaRepository.delete(oferta);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void normalizar(OfertaCuposEmpresa destino, OfertaCuposEmpresa details, boolean actualizacion) {
        Empresa empresa;
        if (actualizacion) {
            empresa = destino.getEmpresa();
            if (details.getEmpresa() != null && details.getEmpresa().getId() != null
                    && !empresa.getId().equals(details.getEmpresa().getId())) {
                throw new IllegalArgumentException("No se puede cambiar la empresa de una oferta existente.");
            }
        } else {
            if (details.getEmpresa() == null || details.getEmpresa().getId() == null) {
                throw new IllegalArgumentException("Selecciona una empresa para la oferta de cupos.");
            }
            empresa = empresaRepository.findById(details.getEmpresa().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa no encontrada."));
        }
        if (Boolean.FALSE.equals(empresa.getActivo()) && Boolean.TRUE.equals(details.getActivo())) {
            throw new IllegalArgumentException("No se puede activar una oferta para una empresa inactiva.");
        }

        String periodo = textoRequerido(details.getPeriodoAcademico(), "El periodo académico es obligatorio.");
        if (periodo.length() > 20) {
            throw new IllegalArgumentException("El periodo académico no puede superar 20 caracteres.");
        }
        if (fechasConvocatoriaRepository.findByPeriodoAcademicoAndTipo(periodo, PROCESO).isEmpty()) {
            throw new IllegalArgumentException(
                    "Configura primero la convocatoria de Prácticas para el periodo " + periodo + ".");
        }
        if (actualizacion && !destino.getPeriodoAcademico().equals(periodo)) {
            throw new IllegalArgumentException("No se puede cambiar el periodo de una oferta existente.");
        }

        String distribucion = textoRequerido(details.getDistribucion(),
                "Selecciona cómo se distribuyen los cupos.").toUpperCase();
        if (!DISTRIBUCIONES.contains(distribucion)) {
            throw new IllegalArgumentException("La distribución debe ser GENERAL o POR_CARRERA.");
        }

        destino.setEmpresa(empresa);
        destino.setPeriodoAcademico(periodo);
        destino.setDistribucion(distribucion);
        destino.setActivo(details.getActivo() == null || details.getActivo());
        destino.setObservacion(vacioANull(details.getObservacion()));
        destino.setUpdatedAt(LocalDateTime.now());

        if ("GENERAL".equals(distribucion)) {
            if (details.getCuposTotales() == null || details.getCuposTotales() <= 0) {
                throw new IllegalArgumentException("La oferta general debe tener al menos un cupo.");
            }
            destino.setCuposTotales(details.getCuposTotales());
            destino.getCarreras().clear();
        } else {
            if (details.getCarreras() == null || details.getCarreras().isEmpty()) {
                throw new IllegalArgumentException("Agrega al menos una carrera a la distribución de cupos.");
            }
            Map<Integer, OfertaCuposEmpresaCarrera> existentesPorCarrera = new HashMap<>();
            for (OfertaCuposEmpresaCarrera existente : destino.getCarreras()) {
                if (existente.getCarrera() != null && existente.getCarrera().getId() != null) {
                    existentesPorCarrera.put(existente.getCarrera().getId(), existente);
                }
            }
            Set<Integer> carrerasUnicas = new HashSet<>();
            List<OfertaCuposEmpresaCarrera> asignacionesNuevas = new ArrayList<>();
            int total = 0;
            for (OfertaCuposEmpresaCarrera item : details.getCarreras()) {
                if (item.getCarrera() == null || item.getCarrera().getId() == null) {
                    throw new IllegalArgumentException("Todas las asignaciones deben indicar una carrera válida.");
                }
                Carrera carrera = carreraRepository.findById(item.getCarrera().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Una carrera seleccionada no existe."));
                if (Boolean.FALSE.equals(carrera.getActivo())) {
                    throw new IllegalArgumentException("No se pueden asignar cupos a una carrera inactiva.");
                }
                if (!carrerasUnicas.add(carrera.getId())) {
                    throw new IllegalArgumentException("No repitas carreras en la distribución de cupos.");
                }
                if (item.getCupos() == null || item.getCupos() <= 0) {
                    throw new IllegalArgumentException("Cada carrera debe recibir al menos un cupo.");
                }
                OfertaCuposEmpresaCarrera asignacion = existentesPorCarrera.get(carrera.getId());
                if (asignacion == null) {
                    asignacion = new OfertaCuposEmpresaCarrera();
                    asignacion.setOferta(destino);
                    asignacionesNuevas.add(asignacion);
                }
                asignacion.setOferta(destino);
                asignacion.setCarrera(carrera);
                asignacion.setCupos(item.getCupos());
                total += item.getCupos();
            }
            destino.setCuposTotales(total);
            destino.getCarreras().removeIf(asignacion -> asignacion.getCarrera() == null
                    || !carrerasUnicas.contains(asignacion.getCarrera().getId()));
            destino.getCarreras().addAll(asignacionesNuevas);
        }
        if (!Boolean.TRUE.equals(destino.getActivo())) {
            int reservados = ofertaComponent.cuposReservados(empresa.getId(), periodo);
            if (reservados > 0) {
                throw new IllegalArgumentException(
                        "No se puede desactivar la oferta mientras existan vacantes con cupos abiertos.");
            }
        }
    }

    private void verificarProceso(Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no tiene acceso a ofertas de cupos de Prácticas.");
        }
    }

    private Optional<Set<Integer>> empresasVisibles(Authentication authentication) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(convenioVigenteComponent::empresasVisiblesPara);
    }

    private void verificarOfertaVisible(
            OfertaCuposEmpresa oferta,
            Authentication authentication) {
        Optional<Set<Integer>> empresasVisibles = empresasVisibles(authentication);
        if (empresasVisibles.isPresent()
                && !empresasVisibles.get().contains(oferta.getEmpresa().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La oferta no pertenece a una empresa visible para tu alcance.");
        }
    }

    private String textoRequerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
        return valor.trim();
    }

    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
