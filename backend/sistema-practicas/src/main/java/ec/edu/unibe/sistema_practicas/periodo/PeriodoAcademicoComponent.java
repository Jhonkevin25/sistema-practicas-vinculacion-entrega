package ec.edu.unibe.sistema_practicas.periodo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PeriodoAcademicoComponent {

    private static final Pattern CODIGO_VALIDO = Pattern.compile("^\\d{4}-[12]$");
    private static final Set<String> ESTADOS = Set.of("PLANIFICADO", "ACTIVO", "CERRADO");

    private final PeriodoAcademicoRepository periodoRepository;

    public PeriodoAcademico exigirConfigurado(String codigo) {
        String normalizado = normalizarCodigo(codigo);
        return periodoRepository.findByCodigo(normalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El periodo académico " + normalizado + " no está configurado."));
    }

    public PeriodoAcademico exigirConfigurable(String codigo) {
        PeriodoAcademico periodo = exigirConfigurado(codigo);
        if ("CERRADO".equals(periodo.getEstado())) {
            throw new IllegalArgumentException(
                    "El periodo académico " + periodo.getCodigo() + " está cerrado.");
        }
        return periodo;
    }

    public PeriodoAcademico exigirActivo(String codigo) {
        PeriodoAcademico periodo = exigirConfigurado(codigo);
        if (!"ACTIVO".equals(periodo.getEstado())) {
            throw new IllegalArgumentException(
                    "El periodo académico " + periodo.getCodigo() + " no está activo.");
        }
        return periodo;
    }

    public PeriodoAcademico activo() {
        return periodoRepository.findByEstado("ACTIVO")
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe un periodo académico activo."));
    }

    public String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El periodo académico es obligatorio.");
        }
        String normalizado = codigo.trim();
        if (!CODIGO_VALIDO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("El periodo académico debe tener el formato AAAA-1 o AAAA-2.");
        }
        return normalizado;
    }

    public String normalizarEstado(String estado) {
        String normalizado = estado == null ? "PLANIFICADO" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("El estado debe ser PLANIFICADO, ACTIVO o CERRADO.");
        }
        return normalizado;
    }
}
