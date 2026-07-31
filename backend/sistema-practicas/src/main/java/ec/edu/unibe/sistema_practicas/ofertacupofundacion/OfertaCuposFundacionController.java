package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import ec.edu.unibe.sistema_practicas.carrera.Carrera;
import ec.edu.unibe.sistema_practicas.carrera.CarreraRepository;
import ec.edu.unibe.sistema_practicas.convenio.ConvenioVigenteComponent;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.fundacion.FundacionRepository;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademicoComponent;
import jakarta.transaction.Transactional;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/ofertas-cupos-fundacion")
@RequiredArgsConstructor
public class OfertaCuposFundacionController {

    private static final String PROCESO = "VINCULACION";
    private static final Set<String> DISTRIBUCIONES = Set.of("GENERAL", "POR_CARRERA");

    private final OfertaCuposFundacionRepository ofertaRepository;
    private final OfertaCuposFundacionComponent ofertaComponent;
    private final FundacionRepository fundacionRepository;
    private final CarreraRepository carreraRepository;
    private final PeriodoAcademicoComponent periodoComponent;
    private final ConvenioVigenteComponent convenioVigenteComponent;
    private final AlcanceCoordinador alcanceCoordinador;

    @GetMapping
    public List<OfertaCuposFundacion> getAll(
            @RequestParam(required = false) String periodoAcademico,
            Authentication authentication) {
        verificarProceso(authentication);
        List<OfertaCuposFundacion> ofertas = periodoAcademico == null || periodoAcademico.isBlank()
                ? ofertaRepository.findAll()
                : ofertaRepository.findByPeriodoAcademico(periodoAcademico.trim());
        Optional<Set<Integer>> fundacionesVisibles = fundacionesVisibles(authentication);
        List<OfertaCuposFundacion> ofertasVisibles = ofertas.stream()
                .filter(oferta -> fundacionesVisibles.isEmpty()
                        || fundacionesVisibles.get().contains(oferta.getFundacion().getId()))
                .toList();
        return ofertaComponent.enriquecerTodas(ofertasVisibles).stream()
                .sorted((a, b) -> a.getFundacion().getNombre()
                        .compareToIgnoreCase(b.getFundacion().getNombre()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfertaCuposFundacion> getById(
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
    public OfertaCuposFundacion create(
            @Valid @RequestBody OfertaCuposFundacion details,
            Authentication authentication) {
        verificarProceso(authentication);
        OfertaCuposFundacion oferta = new OfertaCuposFundacion();
        normalizar(oferta, details, false);
        if (ofertaRepository.existsByFundacionIdAndPeriodoAcademico(
                oferta.getFundacion().getId(), oferta.getPeriodoAcademico())) {
            throw new IllegalArgumentException("La fundación ya tiene una oferta de cupos para este periodo.");
        }
        ofertaComponent.validarConfiguracion(oferta);
        return ofertaComponent.enriquecer(ofertaRepository.save(oferta));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<OfertaCuposFundacion> update(
            @PathVariable Integer id,
            @Valid @RequestBody OfertaCuposFundacion details,
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
                            oferta.getFundacion().getId(), oferta.getPeriodoAcademico());
                    int ocupados = ofertaComponent.cuposOcupados(
                            oferta.getFundacion().getId(), oferta.getPeriodoAcademico());
                    if (reservados + ocupados > 0) {
                        throw new IllegalArgumentException(
                                "No se puede eliminar una oferta con cupos reservados u ocupados.");
                    }
                    ofertaRepository.delete(oferta);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void normalizar(
            OfertaCuposFundacion destino,
            OfertaCuposFundacion details,
            boolean actualizacion) {
        Fundacion fundacion;
        if (actualizacion) {
            fundacion = destino.getFundacion();
            if (details.getFundacion() != null && details.getFundacion().getId() != null
                    && !fundacion.getId().equals(details.getFundacion().getId())) {
                throw new IllegalArgumentException("No se puede cambiar la fundación de una oferta existente.");
            }
        } else {
            if (details.getFundacion() == null || details.getFundacion().getId() == null) {
                throw new IllegalArgumentException("Selecciona una fundación para la oferta de cupos.");
            }
            fundacion = fundacionRepository.findById(details.getFundacion().getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Fundación no encontrada."));
        }
        if (Boolean.FALSE.equals(fundacion.getActiva()) && Boolean.TRUE.equals(details.getActivo())) {
            throw new IllegalArgumentException("No se puede activar una oferta para una fundación inactiva.");
        }

        PeriodoAcademico periodo = periodoComponent.exigirConfigurable(details.getPeriodoAcademico());
        if (actualizacion && !destino.getPeriodoAcademico().equals(periodo.getCodigo())) {
            throw new IllegalArgumentException("No se puede cambiar el periodo de una oferta existente.");
        }

        String distribucion = textoRequerido(
                details.getDistribucion(), "Selecciona cómo se distribuyen los cupos.")
                .toUpperCase(Locale.ROOT);
        if (!DISTRIBUCIONES.contains(distribucion)) {
            throw new IllegalArgumentException("La distribución debe ser GENERAL o POR_CARRERA.");
        }

        destino.setFundacion(fundacion);
        destino.setPeriodoAcademico(periodo.getCodigo());
        destino.setDistribucion(distribucion);
        destino.setActivo(details.getActivo() == null || details.getActivo());
        destino.setObservacion(vacioANull(details.getObservacion()));
        destino.setUpdatedAt(LocalDateTime.now());

        List<OfertaCuposFundacionCarrera> asignaciones = new ArrayList<>();
        if ("GENERAL".equals(distribucion)) {
            if (details.getCuposTotales() == null || details.getCuposTotales() <= 0) {
                throw new IllegalArgumentException("La oferta general debe tener al menos un cupo.");
            }
            destino.setCuposTotales(details.getCuposTotales());
        } else {
            if (details.getCarreras() == null || details.getCarreras().isEmpty()) {
                throw new IllegalArgumentException("Agrega al menos una carrera a la distribución de cupos.");
            }
            Set<Integer> carrerasUnicas = new HashSet<>();
            int total = 0;
            for (OfertaCuposFundacionCarrera item : details.getCarreras()) {
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
                convenioVigenteComponent.exigirParaFundacionEnPeriodo(fundacion, carrera.getNombre(), periodo);
                OfertaCuposFundacionCarrera asignacion = new OfertaCuposFundacionCarrera();
                asignacion.setOferta(destino);
                asignacion.setCarrera(carrera);
                asignacion.setCupos(item.getCupos());
                asignaciones.add(asignacion);
                total += item.getCupos();
            }
            destino.setCuposTotales(total);
        }

        destino.getCarreras().clear();
        destino.getCarreras().addAll(asignaciones);
        if (!Boolean.TRUE.equals(destino.getActivo())) {
            int reservados = ofertaComponent.cuposReservados(fundacion.getId(), periodo.getCodigo());
            if (reservados > 0) {
                throw new IllegalArgumentException(
                        "No se puede desactivar la oferta mientras existan proyectos con cupos abiertos.");
            }
        }
    }

    private void verificarProceso(Authentication authentication) {
        if (!alcanceCoordinador.procesoVisible(authentication, PROCESO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu coordinación no tiene acceso a ofertas de cupos de Vinculación.");
        }
    }

    private Optional<Set<Integer>> fundacionesVisibles(Authentication authentication) {
        return alcanceCoordinador.carrerasVisibles(authentication)
                .map(convenioVigenteComponent::fundacionesVisiblesPara);
    }

    private void verificarOfertaVisible(
            OfertaCuposFundacion oferta,
            Authentication authentication) {
        Optional<Set<Integer>> fundacionesVisibles = fundacionesVisibles(authentication);
        if (fundacionesVisibles.isPresent()
                && !fundacionesVisibles.get().contains(oferta.getFundacion().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La oferta no pertenece a una fundación visible para tu alcance.");
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
