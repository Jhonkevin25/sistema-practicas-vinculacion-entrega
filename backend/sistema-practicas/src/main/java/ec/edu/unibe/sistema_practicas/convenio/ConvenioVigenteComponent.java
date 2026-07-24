package ec.edu.unibe.sistema_practicas.convenio;

import ec.edu.unibe.sistema_practicas.empresa.Empresa;
import ec.edu.unibe.sistema_practicas.fundacion.Fundacion;
import ec.edu.unibe.sistema_practicas.periodo.PeriodoAcademico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ConvenioVigenteComponent {

    private final ConvenioRepository convenioRepository;

    public Convenio exigirParaEmpresa(Empresa empresa, String carrera) {
        if (empresa == null || empresa.getId() == null) {
            throw new IllegalArgumentException("La vacante no tiene una empresa válida asociada.");
        }
        return vigenteParaEmpresa(empresa.getId(), carrera).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La empresa no tiene un convenio vigente que cubra la carrera del estudiante."));
    }

    public Convenio exigirParaFundacion(Fundacion fundacion, String carrera) {
        if (fundacion == null || fundacion.getId() == null) {
            throw new IllegalArgumentException("El proyecto no tiene una fundación válida asociada.");
        }
        return vigenteParaFundacion(fundacion.getId(), carrera).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La fundación no tiene un convenio vigente que cubra la carrera del estudiante."));
    }

    public Convenio exigirParaFundacionEnPeriodo(
            Fundacion fundacion,
            String carrera,
            PeriodoAcademico periodo) {
        if (fundacion == null || fundacion.getId() == null) {
            throw new IllegalArgumentException("El proyecto no tiene una fundación válida asociada.");
        }
        if (periodo == null) {
            throw new IllegalArgumentException("El proyecto no tiene un periodo académico válido.");
        }
        return convenioRepository
                .findByFundacionIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        fundacion.getId(), "VIGENTE", periodo.getFechaInicio(), periodo.getFechaFin())
                .stream()
                .filter(convenio -> cubreCarrera(convenio, carrera))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La fundación no tiene un convenio vigente que cubra " + carrera
                                + " durante el periodo " + periodo.getCodigo() + "."));
    }

    public List<Convenio> vigenteParaEmpresa(Integer empresaId, String carrera) {
        LocalDate hoy = LocalDate.now();
        return convenioRepository
                .findByEmpresaIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        empresaId, "VIGENTE", hoy, hoy)
                .stream()
                .filter(convenio -> cubreCarrera(convenio, carrera))
                .toList();
    }

    public List<Convenio> vigenteParaFundacion(Integer fundacionId, String carrera) {
        LocalDate hoy = LocalDate.now();
        return convenioRepository
                .findByFundacionIdAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        fundacionId, "VIGENTE", hoy, hoy)
                .stream()
                .filter(convenio -> cubreCarrera(convenio, carrera))
                .toList();
    }

    public Set<Integer> empresasVisiblesPara(Set<String> carreras) {
        if (carreras == null || carreras.isEmpty()) return Set.of();
        LocalDate hoy = LocalDate.now();
        Set<String> normalizadas = normalizarCarreras(carreras);
        return convenioRepository
                .findByEmpresaIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        "VIGENTE", hoy, hoy)
                .stream()
                .filter(convenio -> cubreAlgunaCarrera(convenio, normalizadas))
                .map(Convenio::getEmpresa)
                .filter(empresa -> empresa != null && empresa.getId() != null)
                .map(Empresa::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Integer> fundacionesVisiblesPara(Set<String> carreras) {
        if (carreras == null || carreras.isEmpty()) return Set.of();
        LocalDate hoy = LocalDate.now();
        Set<String> normalizadas = normalizarCarreras(carreras);
        return convenioRepository
                .findByFundacionIsNotNullAndEstadoIgnoreCaseAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        "VIGENTE", hoy, hoy)
                .stream()
                .filter(convenio -> cubreAlgunaCarrera(convenio, normalizadas))
                .map(Convenio::getFundacion)
                .filter(fundacion -> fundacion != null && fundacion.getId() != null)
                .map(Fundacion::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean cubreAlgunaCarrera(Convenio convenio, Set<String> carrerasNormalizadas) {
        if (convenio.getCarreras() == null || convenio.getCarreras().isEmpty()) {
            return true;
        }
        return convenio.getCarreras().stream()
                .map(this::normalizarCarrera)
                .anyMatch(carrerasNormalizadas::contains);
    }

    private Set<String> normalizarCarreras(Set<String> carreras) {
        return carreras.stream()
                .filter(carrera -> carrera != null && !carrera.isBlank())
                .map(this::normalizarCarrera)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean cubreCarrera(Convenio convenio, String carrera) {
        if (convenio.getCarreras() == null || convenio.getCarreras().isEmpty()) {
            return true;
        }
        if (carrera == null || carrera.isBlank()) {
            return false;
        }
        String objetivo = normalizarCarrera(carrera);
        return convenio.getCarreras().stream()
                .anyMatch(cubierta -> normalizarCarrera(cubierta).equals(objetivo));
    }

    // Insensible a mayusculas y tildes: "Ingenieria en Software" e
    // "Ingeniería en Software" son la misma carrera.
    private String normalizarCarrera(String texto) {
        String plano = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return plano.toLowerCase();
    }
}
