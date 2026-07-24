package ec.edu.unibe.sistema_practicas.ofertacupofundacion;

import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoCarrera;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OfertaCuposFundacionComponent {

    private final OfertaCuposFundacionRepository ofertaRepository;
    private final ProyectoRepository proyectoRepository;
    private final VinculacionRepository vinculacionRepository;

    public OfertaCuposFundacion exigirDisponibilidad(
            Integer fundacionId,
            String periodo,
            int cuposSolicitados,
            Map<String, Integer> cuposPorCarrera,
            Integer proyectoActualId) {
        OfertaCuposFundacion oferta = ofertaRepository
                .findByFundacionIdAndPeriodoAcademicoForUpdate(fundacionId, periodo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La fundación no tiene una oferta de cupos configurada para el periodo " + periodo + "."));
        if (!Boolean.TRUE.equals(oferta.getActivo())) {
            throw new IllegalArgumentException("La oferta de cupos de la fundación está inactiva.");
        }

        Compromisos compromisos = compromisos(fundacionId, periodo, proyectoActualId);
        if ("GENERAL".equals(oferta.getDistribucion())) {
            int disponibles = Math.max(0,
                    oferta.getCuposTotales() - compromisos.reservadosTotales() - compromisos.ocupadosTotales());
            if (cuposSolicitados > disponibles) {
                throw new IllegalArgumentException("La fundación solo tiene " + disponibles
                        + " cupo(s) disponibles para este periodo.");
            }
            return oferta;
        }

        if (cuposPorCarrera == null || cuposPorCarrera.isEmpty()) {
            throw new IllegalArgumentException(
                    "La oferta está distribuida por carrera; asigna los cupos de cada carrera del proyecto.");
        }
        Map<String, Integer> configurados = cuposConfigurados(oferta);
        for (Map.Entry<String, Integer> solicitud : cuposPorCarrera.entrySet()) {
            String carrera = normalizarCarrera(solicitud.getKey());
            int disponibles = Math.max(0,
                    configurados.getOrDefault(carrera, 0)
                            - compromisos.reservadosPorCarrera().getOrDefault(carrera, 0)
                            - compromisos.ocupadosPorCarrera().getOrDefault(carrera, 0));
            if (solicitud.getValue() > disponibles) {
                throw new IllegalArgumentException("La fundación solo tiene " + disponibles
                        + " cupo(s) disponibles para " + solicitud.getKey() + " en este periodo.");
            }
        }
        return oferta;
    }

    public OfertaCuposFundacion enriquecer(OfertaCuposFundacion oferta) {
        if (oferta == null || oferta.getFundacion() == null || oferta.getFundacion().getId() == null) {
            return oferta;
        }
        Compromisos compromisos = compromisos(
                oferta.getFundacion().getId(), oferta.getPeriodoAcademico(), null);
        oferta.setCuposReservados(compromisos.reservadosTotales());
        oferta.setCuposOcupados(compromisos.ocupadosTotales());
        oferta.setCuposDisponibles(Math.max(0,
                oferta.getCuposTotales() - compromisos.reservadosTotales() - compromisos.ocupadosTotales()));

        for (OfertaCuposFundacionCarrera asignacion : oferta.getCarreras()) {
            String carrera = normalizarCarrera(asignacion.getCarrera().getNombre());
            int reservados = compromisos.reservadosPorCarrera().getOrDefault(carrera, 0);
            int ocupados = compromisos.ocupadosPorCarrera().getOrDefault(carrera, 0);
            asignacion.setCuposReservados(reservados);
            asignacion.setCuposOcupados(ocupados);
            asignacion.setCuposDisponibles(Math.max(0, asignacion.getCupos() - reservados - ocupados));
        }
        return oferta;
    }

    public void validarConfiguracion(OfertaCuposFundacion oferta) {
        Compromisos compromisos = compromisos(
                oferta.getFundacion().getId(), oferta.getPeriodoAcademico(), null);
        if ("GENERAL".equals(oferta.getDistribucion())) {
            int comprometidos = compromisos.reservadosTotales() + compromisos.ocupadosTotales();
            if (oferta.getCuposTotales() < comprometidos) {
                throw new IllegalArgumentException("La oferta no puede ser menor que los "
                        + comprometidos + " cupos ya reservados u ocupados.");
            }
            return;
        }

        if (compromisos.reservasSinDistribuir()) {
            throw new IllegalArgumentException(
                    "No se puede usar distribución por carrera mientras existan proyectos con cupos generales sin distribuir.");
        }
        Map<String, Integer> configurados = cuposConfigurados(oferta);
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

    public int cuposReservados(Integer fundacionId, String periodo) {
        return compromisos(fundacionId, periodo, null).reservadosTotales();
    }

    public int cuposOcupados(Integer fundacionId, String periodo) {
        return compromisos(fundacionId, periodo, null).ocupadosTotales();
    }

    public static boolean mismaCarrera(String izquierda, String derecha) {
        return normalizarCarrera(izquierda).equals(normalizarCarrera(derecha));
    }

    private Map<String, Integer> cuposConfigurados(OfertaCuposFundacion oferta) {
        Map<String, Integer> resultado = new HashMap<>();
        for (OfertaCuposFundacionCarrera asignacion : oferta.getCarreras()) {
            resultado.put(normalizarCarrera(asignacion.getCarrera().getNombre()), asignacion.getCupos());
        }
        return resultado;
    }

    private Compromisos compromisos(Integer fundacionId, String periodo, Integer proyectoExcluidoId) {
        List<Proyecto> proyectos = proyectoRepository.findByFundacionIdAndPeriodo(fundacionId, periodo).stream()
                .filter(proyecto -> proyectoExcluidoId == null || !proyectoExcluidoId.equals(proyecto.getId()))
                .toList();
        List<Vinculacion> vinculaciones = vinculacionRepository
                .findByFundacionIdAndPeriodoAcademico(fundacionId, periodo).stream()
                .filter(vinculacion -> proyectoExcluidoId == null
                        || vinculacion.getProyecto() == null
                        || !proyectoExcluidoId.equals(vinculacion.getProyecto().getId()))
                // Fase 42: una vinculación retirada con cupo liberado deja de
                // ocupar capacidad (el cupo volvió al proyecto para reemplazo)
                .filter(vinculacion -> !Boolean.TRUE.equals(vinculacion.getCupoLiberado()))
                .toList();

        int reservadosTotales = 0;
        boolean reservasSinDistribuir = false;
        Map<String, Integer> reservadosPorCarrera = new HashMap<>();
        for (Proyecto proyecto : proyectos) {
            int reservados = valor(proyecto.getCuposDisponibles());
            reservadosTotales += reservados;
            List<ProyectoCarrera> carreras = proyecto.getCarreras() == null
                    ? List.of()
                    : proyecto.getCarreras();
            boolean distribuidas = !carreras.isEmpty()
                    && carreras.stream().allMatch(carrera -> carrera.getCuposDisponibles() != null);
            if (distribuidas) {
                for (ProyectoCarrera carrera : carreras) {
                    reservadosPorCarrera.merge(
                            normalizarCarrera(carrera.getCarrera().getNombre()),
                            valor(carrera.getCuposDisponibles()),
                            Integer::sum);
                }
            } else if (carreras.size() == 1) {
                reservadosPorCarrera.merge(
                        normalizarCarrera(carreras.get(0).getCarrera().getNombre()),
                        reservados,
                        Integer::sum);
            } else if (reservados > 0) {
                reservasSinDistribuir = true;
            }
        }

        Map<String, Integer> ocupadosPorCarrera = new HashMap<>();
        for (Vinculacion vinculacion : vinculaciones) {
            String carrera = vinculacion.getEstudiante() == null
                    ? ""
                    : vinculacion.getEstudiante().getCarrera();
            ocupadosPorCarrera.merge(normalizarCarrera(carrera), 1, Integer::sum);
        }
        return new Compromisos(
                reservadosTotales,
                vinculaciones.size(),
                reservadosPorCarrera,
                ocupadosPorCarrera,
                reservasSinDistribuir);
    }

    private int valor(Integer numero) {
        return numero == null ? 0 : Math.max(0, numero);
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
            Map<String, Integer> ocupadosPorCarrera,
            boolean reservasSinDistribuir) {
    }
}
