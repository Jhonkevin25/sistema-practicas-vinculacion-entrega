package ec.edu.unibe.sistema_practicas.ofertacupo;

import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.vacante.VacantePractica;
import ec.edu.unibe.sistema_practicas.vacante.VacantePracticaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OfertaCuposEmpresaComponent {

    private final OfertaCuposEmpresaRepository ofertaRepository;
    private final VacantePracticaRepository vacanteRepository;
    private final PracticaRepository practicaRepository;

    public OfertaCuposEmpresa exigirDisponibilidad(Integer empresaId, String periodo,
                                                    String carrera, int cuposSolicitados) {
        return exigirDisponibilidad(empresaId, periodo, carrera, cuposSolicitados, null);
    }

    // Fase 42: al editar una vacante existente se excluye su propia reserva
    // para no contarla dos veces contra la oferta de la empresa.
    public OfertaCuposEmpresa exigirDisponibilidad(Integer empresaId, String periodo,
                                                    String carrera, int cuposSolicitados,
                                                    Integer vacanteExcluidaId) {
        OfertaCuposEmpresa oferta = ofertaRepository
                .findByEmpresaIdAndPeriodoAcademicoForUpdate(empresaId, periodo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La empresa no tiene una oferta de cupos configurada para el periodo " + periodo + "."));
        if (!Boolean.TRUE.equals(oferta.getActivo())) {
            throw new IllegalArgumentException("La oferta de cupos de la empresa está inactiva.");
        }
        enriquecer(oferta, vacanteExcluidaId);
        int disponibles = disponiblesParaCarrera(oferta, carrera);
        if (cuposSolicitados > disponibles) {
            throw new IllegalArgumentException("La empresa solo tiene " + disponibles
                    + " cupo(s) disponibles para esta carrera y periodo.");
        }
        return oferta;
    }

    public OfertaCuposEmpresa enriquecer(OfertaCuposEmpresa oferta) {
        return enriquecer(oferta, null);
    }

    private OfertaCuposEmpresa enriquecer(OfertaCuposEmpresa oferta, Integer vacanteExcluidaId) {
        if (oferta == null || oferta.getEmpresa() == null || oferta.getEmpresa().getId() == null) {
            return oferta;
        }
        Compromisos compromisos = compromisos(
                oferta.getEmpresa().getId(), oferta.getPeriodoAcademico(), vacanteExcluidaId);
        oferta.setCuposReservados(compromisos.reservadosTotales());
        oferta.setCuposOcupados(compromisos.ocupadosTotales());
        oferta.setCuposDisponibles(Math.max(0,
                oferta.getCuposTotales() - compromisos.reservadosTotales() - compromisos.ocupadosTotales()));

        for (OfertaCuposEmpresaCarrera asignacion : oferta.getCarreras()) {
            String clave = normalizarCarrera(asignacion.getCarrera().getNombre());
            int reservados = compromisos.reservadosPorCarrera().getOrDefault(clave, 0);
            int ocupados = compromisos.ocupadosPorCarrera().getOrDefault(clave, 0);
            asignacion.setCuposReservados(reservados);
            asignacion.setCuposOcupados(ocupados);
            asignacion.setCuposDisponibles(Math.max(0, asignacion.getCupos() - reservados - ocupados));
        }
        return oferta;
    }

    public void validarConfiguracion(OfertaCuposEmpresa oferta) {
        Compromisos compromisos = compromisos(
                oferta.getEmpresa().getId(), oferta.getPeriodoAcademico(), null);
        if ("GENERAL".equals(oferta.getDistribucion())) {
            int comprometidos = compromisos.reservadosTotales() + compromisos.ocupadosTotales();
            if (oferta.getCuposTotales() < comprometidos) {
                throw new IllegalArgumentException("La oferta no puede ser menor que los "
                        + comprometidos + " cupos ya reservados u ocupados.");
            }
            return;
        }

        Map<String, Integer> configurados = new HashMap<>();
        for (OfertaCuposEmpresaCarrera asignacion : oferta.getCarreras()) {
            configurados.put(normalizarCarrera(asignacion.getCarrera().getNombre()), asignacion.getCupos());
        }
        Map<String, Integer> comprometidos = new HashMap<>(compromisos.reservadosPorCarrera());
        compromisos.ocupadosPorCarrera().forEach(
                (carrera, total) -> comprometidos.merge(carrera, total, Integer::sum));
        comprometidos.forEach((carrera, total) -> {
            if (configurados.getOrDefault(carrera, 0) < total) {
                throw new IllegalArgumentException("La distribución por carrera no cubre "
                        + total + " cupo(s) ya reservados u ocupados en " + carrera + ".");
            }
        });
    }

    public int cuposReservados(Integer empresaId, String periodo) {
        return compromisos(empresaId, periodo, null).reservadosTotales();
    }

    public int cuposOcupados(Integer empresaId, String periodo) {
        return compromisos(empresaId, periodo, null).ocupadosTotales();
    }

    public static boolean mismaCarrera(String izquierda, String derecha) {
        return normalizarCarrera(izquierda).equals(normalizarCarrera(derecha));
    }

    private int disponiblesParaCarrera(OfertaCuposEmpresa oferta, String carrera) {
        if ("GENERAL".equals(oferta.getDistribucion())) {
            return oferta.getCuposDisponibles();
        }
        return oferta.getCarreras().stream()
                .filter(asignacion -> mismaCarrera(asignacion.getCarrera().getNombre(), carrera))
                .map(OfertaCuposEmpresaCarrera::getCuposDisponibles)
                .findFirst()
                .orElse(0);
    }

    private Compromisos compromisos(Integer empresaId, String periodo, Integer vacanteExcluidaId) {
        List<VacantePractica> vacantes = vacanteRepository
                .findByEmpresaIdAndPeriodoAcademico(empresaId, periodo).stream()
                .filter(vacante -> vacanteExcluidaId == null || !vacanteExcluidaId.equals(vacante.getId()))
                .toList();
        List<Practica> practicas = practicaRepository
                .findByEmpresaIdAndPeriodoAcademico(empresaId, periodo);

        Map<String, Integer> reservadosPorCarrera = new HashMap<>();
        int reservadosTotales = 0;
        for (VacantePractica vacante : vacantes) {
            int cupos = vacante.getCupos() == null ? 0 : Math.max(0, vacante.getCupos());
            reservadosTotales += cupos;
            reservadosPorCarrera.merge(normalizarCarrera(vacante.getCarrera()), cupos, Integer::sum);
        }

        Map<String, Integer> ocupadosPorCarrera = new HashMap<>();
        int ocupadosTotales = 0;
        for (Practica practica : practicas) {
            // Fase 47: una práctica retirada con cupo liberado deja de ocupar cupo
            if (Boolean.TRUE.equals(practica.getCupoLiberado())) {
                continue;
            }
            ocupadosTotales++;
            String carrera = practica.getEstudiante() == null ? "" : practica.getEstudiante().getCarrera();
            ocupadosPorCarrera.merge(normalizarCarrera(carrera), 1, Integer::sum);
        }
        return new Compromisos(reservadosTotales, ocupadosTotales,
                reservadosPorCarrera, ocupadosPorCarrera);
    }

    private static String normalizarCarrera(String carrera) {
        if (carrera == null) return "";
        return Normalizer.normalize(carrera, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private record Compromisos(
            int reservadosTotales,
            int ocupadosTotales,
            Map<String, Integer> reservadosPorCarrera,
            Map<String, Integer> ocupadosPorCarrera) {
    }
}
