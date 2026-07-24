package ec.edu.unibe.sistema_practicas.reporte;

import ec.edu.unibe.sistema_practicas.asistencia.Asistencia;
import ec.edu.unibe.sistema_practicas.asistencia.AsistenciaRepository;
import ec.edu.unibe.sistema_practicas.bitacora.Bitacora;
import ec.edu.unibe.sistema_practicas.bitacora.BitacoraRepository;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresa;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaCarrera;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaComponent;
import ec.edu.unibe.sistema_practicas.ofertacupo.OfertaCuposEmpresaRepository;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.proyecto.Proyecto;
import ec.edu.unibe.sistema_practicas.proyecto.ProyectoRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private static final String PRACTICAS = "PRACTICAS";
    private static final String VINCULACION = "VINCULACION";

    private final PracticaRepository practicaRepository;
    private final VinculacionRepository vinculacionRepository;
    private final OfertaCuposEmpresaRepository ofertaCuposEmpresaRepository;
    private final OfertaCuposEmpresaComponent ofertaCuposEmpresaComponent;
    private final ProyectoRepository proyectoRepository;
    private final BitacoraRepository bitacoraRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final AlcanceCoordinador alcanceCoordinador;

    @GetMapping("/asignaciones")
    public List<AsignacionReporte> asignaciones(Authentication authentication,
                                                 @RequestParam(required = false) String periodo,
                                                 @RequestParam(required = false) String carrera,
                                                 @RequestParam(required = false) String proceso,
                                                 @RequestParam(required = false) String entidad) {
        verificarGestionAcademica(authentication);
        String procesoNormalizado = normalizarProceso(proceso);
        Set<String> carreras = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);
        List<AsignacionReporte> filas = new ArrayList<>();

        if (incluye(authentication, procesoNormalizado, PRACTICAS)) {
            practicaRepository.findAll().stream()
                    .filter(p -> visible(carreras, p.getEstudiante()))
                    .filter(p -> coincideExacto(p.getPeriodoAcademico(), periodo))
                    .filter(p -> coincideCarrera(p.getEstudiante(), carrera))
                    .filter(p -> coincideTexto(nombreEmpresa(p), entidad))
                    .map(p -> new AsignacionReporte(
                            PRACTICAS, p.getId(), idEstudiante(p.getEstudiante()),
                            nombreEstudiante(p.getEstudiante()), matricula(p.getEstudiante()),
                            carrera(p.getEstudiante()), p.getPeriodoAcademico(), nombreEmpresa(p),
                            p.getEstado(), p.getFechaInicio(), p.getFechaFin(),
                            valor(p.getHorasRequeridas()), valor(p.getHorasCompletadas())))
                    .forEach(filas::add);
        }
        if (incluye(authentication, procesoNormalizado, VINCULACION)) {
            vinculacionRepository.findAll().stream()
                    .filter(v -> visible(carreras, v.getEstudiante()))
                    .filter(v -> coincideExacto(v.getPeriodoAcademico(), periodo))
                    .filter(v -> coincideCarrera(v.getEstudiante(), carrera))
                    .filter(v -> coincideTexto(nombreEntidad(v), entidad))
                    .map(v -> new AsignacionReporte(
                            VINCULACION, v.getId(), idEstudiante(v.getEstudiante()),
                            nombreEstudiante(v.getEstudiante()), matricula(v.getEstudiante()),
                            carrera(v.getEstudiante()), v.getPeriodoAcademico(), nombreEntidad(v),
                            v.getEstado(), v.getFechaInicio(), v.getFechaFin(),
                            valor(v.getHorasRequeridas()), valor(v.getHorasCompletadas())))
                    .forEach(filas::add);
        }
        return filas.stream()
                .sorted(Comparator.comparing(AsignacionReporte::proceso)
                        .thenComparing(AsignacionReporte::estudiante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @GetMapping("/cupos")
    public List<CupoReporte> cupos(Authentication authentication,
                                   @RequestParam(required = false) String periodo,
                                   @RequestParam(required = false) String carrera,
                                   @RequestParam(required = false) String proceso,
                                   @RequestParam(required = false) String entidad) {
        verificarGestionAcademica(authentication);
        String procesoNormalizado = normalizarProceso(proceso);
        Set<String> carreras = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);
        List<CupoReporte> filas = new ArrayList<>();

        if (incluye(authentication, procesoNormalizado, PRACTICAS)) {
            filas.addAll(cuposPracticas(carreras, periodo, carrera, entidad));
        }
        if (incluye(authentication, procesoNormalizado, VINCULACION)) {
            filas.addAll(cuposVinculacion(carreras, periodo, carrera, entidad));
        }
        return filas.stream()
                .sorted(Comparator.comparing(CupoReporte::proceso)
                        .thenComparing(CupoReporte::entidad, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CupoReporte::carrera, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @GetMapping("/riesgos")
    public List<RiesgoReporte> riesgos(Authentication authentication,
                                       @RequestParam(required = false) String periodo,
                                       @RequestParam(required = false) String carrera,
                                       @RequestParam(required = false) String proceso,
                                       @RequestParam(required = false) String entidad) {
        verificarGestionAcademica(authentication);
        String procesoNormalizado = normalizarProceso(proceso);
        Set<String> carreras = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);
        Map<Integer, List<Asistencia>> asistenciasPractica = asistenciaRepository.findAll().stream()
                .filter(a -> a.getPractica() != null && a.getPractica().getId() != null)
                .collect(Collectors.groupingBy(a -> a.getPractica().getId()));
        Map<Integer, List<Asistencia>> asistenciasVinculacion = asistenciaRepository.findAll().stream()
                .filter(a -> a.getVinculacion() != null && a.getVinculacion().getId() != null)
                .collect(Collectors.groupingBy(a -> a.getVinculacion().getId()));
        Map<Integer, List<Bitacora>> bitacorasPractica = bitacoraRepository.findAll().stream()
                .filter(b -> b.getPractica() != null && b.getPractica().getId() != null)
                .collect(Collectors.groupingBy(b -> b.getPractica().getId()));
        Map<Integer, List<Bitacora>> bitacorasVinculacion = bitacoraRepository.findAll().stream()
                .filter(b -> b.getVinculacion() != null && b.getVinculacion().getId() != null)
                .collect(Collectors.groupingBy(b -> b.getVinculacion().getId()));
        List<RiesgoReporte> filas = new ArrayList<>();

        if (incluye(authentication, procesoNormalizado, PRACTICAS)) {
            practicaRepository.findAll().stream()
                    .filter(p -> visible(carreras, p.getEstudiante()))
                    .filter(p -> coincideExacto(p.getPeriodoAcademico(), periodo))
                    .filter(p -> coincideCarrera(p.getEstudiante(), carrera))
                    .filter(p -> coincideTexto(nombreEmpresa(p), entidad))
                    .map(p -> riesgoPractica(p, bitacorasPractica.getOrDefault(p.getId(), List.of()),
                            asistenciasPractica.getOrDefault(p.getId(), List.of())))
                    .flatMap(Optional::stream)
                    .forEach(filas::add);
        }
        if (incluye(authentication, procesoNormalizado, VINCULACION)) {
            vinculacionRepository.findAll().stream()
                    .filter(v -> visible(carreras, v.getEstudiante()))
                    .filter(v -> coincideExacto(v.getPeriodoAcademico(), periodo))
                    .filter(v -> coincideCarrera(v.getEstudiante(), carrera))
                    .filter(v -> coincideTexto(nombreEntidad(v), entidad))
                    .map(v -> riesgoVinculacion(v, bitacorasVinculacion.getOrDefault(v.getId(), List.of()),
                            asistenciasVinculacion.getOrDefault(v.getId(), List.of())))
                    .flatMap(Optional::stream)
                    .forEach(filas::add);
        }
        return filas.stream()
                .sorted(Comparator.comparingInt((RiesgoReporte fila) -> "ALTO".equals(fila.nivel()) ? 0 : 1)
                        .thenComparing(RiesgoReporte::estudiante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @GetMapping("/cierres")
    public List<CierreReporte> cierres(Authentication authentication,
                                       @RequestParam(required = false) String periodo,
                                       @RequestParam(required = false) String carrera,
                                       @RequestParam(required = false) String proceso,
                                       @RequestParam(required = false) String entidad) {
        verificarGestionAcademica(authentication);
        String procesoNormalizado = normalizarProceso(proceso);
        Set<String> carreras = alcanceCoordinador.carrerasVisibles(authentication).orElse(null);
        List<CierreReporte> filas = new ArrayList<>();

        if (incluye(authentication, procesoNormalizado, PRACTICAS)) {
            practicaRepository.findAll().stream()
                    .filter(p -> visible(carreras, p.getEstudiante()))
                    .filter(p -> coincideExacto(p.getPeriodoAcademico(), periodo))
                    .filter(p -> coincideCarrera(p.getEstudiante(), carrera))
                    .filter(p -> coincideTexto(nombreEmpresa(p), entidad))
                    .map(p -> cierre(PRACTICAS, p.getId(), p.getEstudiante(), p.getPeriodoAcademico(),
                            nombreEmpresa(p), p.getEstado(), p.getHorasRequeridas(), p.getHorasCompletadas(), p.getCerradoEn()))
                    .forEach(filas::add);
        }
        if (incluye(authentication, procesoNormalizado, VINCULACION)) {
            vinculacionRepository.findAll().stream()
                    .filter(v -> visible(carreras, v.getEstudiante()))
                    .filter(v -> coincideExacto(v.getPeriodoAcademico(), periodo))
                    .filter(v -> coincideCarrera(v.getEstudiante(), carrera))
                    .filter(v -> coincideTexto(nombreEntidad(v), entidad))
                    .map(v -> cierre(VINCULACION, v.getId(), v.getEstudiante(), v.getPeriodoAcademico(),
                            nombreEntidad(v), v.getEstado(), v.getHorasRequeridas(), v.getHorasCompletadas(), v.getCerradoEn()))
                    .forEach(filas::add);
        }
        return filas.stream()
                .sorted(Comparator.comparing(CierreReporte::proceso)
                        .thenComparing(CierreReporte::estudiante, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    // Fase 43: Endpoints de paginación (enfoque híbrido: agrupados en memoria pero paginados para el frontend)
    @GetMapping("/asignaciones/paginado")
    public ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse<AsignacionReporte> asignacionesPaginadas(
            Authentication authentication,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) String proceso,
            @RequestParam(required = false) String entidad,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        List<AsignacionReporte> filas = asignaciones(authentication, periodo, carrera, proceso, entidad);
        return ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse.deLista(filas, pagina, tamano);
    }

    @GetMapping("/cupos/paginado")
    public ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse<CupoReporte> cuposPaginados(
            Authentication authentication,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) String proceso,
            @RequestParam(required = false) String entidad,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        List<CupoReporte> filas = cupos(authentication, periodo, carrera, proceso, entidad);
        return ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse.deLista(filas, pagina, tamano);
    }

    @GetMapping("/riesgos/paginado")
    public ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse<RiesgoReporte> riesgosPaginados(
            Authentication authentication,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) String proceso,
            @RequestParam(required = false) String entidad,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        List<RiesgoReporte> filas = riesgos(authentication, periodo, carrera, proceso, entidad);
        return ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse.deLista(filas, pagina, tamano);
    }

    @GetMapping("/cierres/paginado")
    public ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse<CierreReporte> cierresPaginados(
            Authentication authentication,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String carrera,
            @RequestParam(required = false) String proceso,
            @RequestParam(required = false) String entidad,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        List<CierreReporte> filas = cierres(authentication, periodo, carrera, proceso, entidad);
        return ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse.deLista(filas, pagina, tamano);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(Authentication authentication,
                                            @RequestParam String tipo,
                                            @RequestParam(required = false) String periodo,
                                            @RequestParam(required = false) String carrera,
                                            @RequestParam(required = false) String proceso,
                                            @RequestParam(required = false) String entidad) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        String csv = switch (tipoNormalizado) {
            case "ASIGNACIONES" -> csvAsignaciones(asignaciones(authentication, periodo, carrera, proceso, entidad));
            case "CUPOS" -> csvCupos(cupos(authentication, periodo, carrera, proceso, entidad));
            case "RIESGOS" -> csvRiesgos(riesgos(authentication, periodo, carrera, proceso, entidad));
            case "CIERRES" -> csvCierres(cierres(authentication, periodo, carrera, proceso, entidad));
            default -> throw new IllegalArgumentException("El tipo de reporte debe ser ASIGNACIONES, CUPOS, RIESGOS o CIERRES.");
        };
        byte[] contenido = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte_" + tipoNormalizado.toLowerCase(Locale.ROOT) + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(contenido);
    }

    private List<CupoReporte> cuposPracticas(Set<String> carreras, String periodo, String carreraFiltro,
                                              String entidadFiltro) {
        List<OfertaCuposEmpresa> ofertas = vacio(periodo)
                ? ofertaCuposEmpresaRepository.findAll()
                : ofertaCuposEmpresaRepository.findByPeriodoAcademico(periodo);
        List<CupoReporte> filas = new ArrayList<>();
        for (OfertaCuposEmpresa oferta : ofertas) {
            if (oferta.getEmpresa() == null
                    || !coincideTexto(oferta.getEmpresa().getNombre(), entidadFiltro)) continue;
            ofertaCuposEmpresaComponent.enriquecer(oferta);
            if ("GENERAL".equals(oferta.getDistribucion())) {
                if (!vacio(carreraFiltro)) continue;
                filas.add(cupoOferta(oferta.getEmpresa().getNombre(), "General",
                        oferta.getPeriodoAcademico(), oferta.getCuposTotales(), oferta.getCuposDisponibles()));
                continue;
            }
            for (OfertaCuposEmpresaCarrera asignacion : oferta.getCarreras()) {
                String carrera = asignacion.getCarrera().getNombre();
                if (!visible(carreras, carrera) || !coincideExacto(carrera, carreraFiltro)) continue;
                filas.add(cupoOferta(oferta.getEmpresa().getNombre(), carrera,
                        oferta.getPeriodoAcademico(), asignacion.getCupos(), asignacion.getCuposDisponibles()));
            }
        }
        return filas;
    }

    private CupoReporte cupoOferta(String entidad, String carrera, String periodo,
                                    Integer totalesValor, Integer disponiblesValor) {
        int totales = valor(totalesValor);
        int disponibles = Math.max(0, valor(disponiblesValor));
        int usados = Math.max(0, totales - disponibles);
        int ocupacion = totales == 0 ? 0 : Math.min(100, Math.round((usados * 100f) / totales));
        return new CupoReporte(PRACTICAS, entidad, carrera, periodo,
                totales, usados, disponibles, ocupacion);
    }

    private List<CupoReporte> cuposVinculacion(Set<String> carreras, String periodo, String carreraFiltro,
                                                String entidadFiltro) {
        List<Proyecto> proyectos = proyectoRepository.findAll();
        List<Vinculacion> vinculaciones = vinculacionRepository.findAll();
        Map<Integer, List<Vinculacion>> visiblesPorProyecto = vinculaciones.stream()
                .filter(v -> v.getProyecto() != null && visible(carreras, v.getEstudiante()))
                .filter(v -> coincideExacto(v.getPeriodoAcademico(), periodo))
                .filter(v -> coincideCarrera(v.getEstudiante(), carreraFiltro))
                .filter(v -> coincideTexto(nombreEntidad(v), entidadFiltro))
                .collect(Collectors.groupingBy(v -> v.getProyecto().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<String, CupoAcumulado> acumulados = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Vinculacion>> entry : visiblesPorProyecto.entrySet()) {
            Proyecto proyecto = proyectos.stream().filter(p -> p.getId().equals(entry.getKey())).findFirst().orElse(null);
            if (proyecto == null) continue;
            List<String> carrerasProyecto = entry.getValue().stream()
                    .map(Vinculacion::getEstudiante)
                    .map(this::carrera)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            CupoAcumulado acumulado = new CupoAcumulado(
                    VINCULACION,
                    nombreProyecto(proyecto),
                    carrerasProyecto.isEmpty() ? "Sin asignación" : String.join(" / ", carrerasProyecto),
                    periodo);
            acumulado.disponibles = valor(proyecto.getCuposDisponibles());
            acumulado.usados = entry.getValue().size();
            acumulados.put(String.valueOf(proyecto.getId()), acumulado);
        }
        if (carreras == null && vacio(carreraFiltro)) {
            for (Proyecto proyecto : proyectos) {
                boolean incluido = acumulados.containsKey(String.valueOf(proyecto.getId()));
                String nombre = nombreProyecto(proyecto);
                if (!incluido && coincideTexto(nombre, entidadFiltro) && coincideExacto(proyecto.getPeriodo(), periodo)) {
                    CupoAcumulado acumulado = new CupoAcumulado(VINCULACION, nombre, "Sin asignación", proyecto.getPeriodo());
                    acumulado.disponibles = valor(proyecto.getCuposDisponibles());
                    acumulados.put(String.valueOf(proyecto.getId()), acumulado);
                }
            }
        }
        return acumulados.values().stream().map(CupoAcumulado::toResponse).toList();
    }

    private Optional<RiesgoReporte> riesgoPractica(Practica practica, List<Bitacora> bitacoras,
                                                    List<Asistencia> asistencias) {
        return riesgo(PRACTICAS, practica.getId(), practica.getEstudiante(), practica.getPeriodoAcademico(),
                nombreEmpresa(practica), practica.getEstado(), practica.getFechaInicio(), practica.getFechaFin(),
                practica.getHorasRequeridas(), practica.getHorasCompletadas(), bitacoras, asistencias);
    }

    private Optional<RiesgoReporte> riesgoVinculacion(Vinculacion vinculacion, List<Bitacora> bitacoras,
                                                       List<Asistencia> asistencias) {
        return riesgo(VINCULACION, vinculacion.getId(), vinculacion.getEstudiante(), vinculacion.getPeriodoAcademico(),
                nombreEntidad(vinculacion), vinculacion.getEstado(), vinculacion.getFechaInicio(), vinculacion.getFechaFin(),
                vinculacion.getHorasRequeridas(), vinculacion.getHorasCompletadas(), bitacoras, asistencias);
    }

    private Optional<RiesgoReporte> riesgo(String proceso, Integer expedienteId, Estudiante estudiante,
                                            String periodo, String entidad, String estado, LocalDate fechaInicio,
                                            LocalDate fechaFin, Integer horasRequeridas, Integer horasCompletadas,
                                            List<Bitacora> bitacoras, List<Asistencia> asistencias) {
        // Solo cuentan las observadas sin una aprobada posterior del mismo
        // parcial (la corrección aprobada las resuelve)
        long observadas = observadasSinResolver(bitacoras);
        Integer diasSinActividad = diasSinActividad(fechaInicio, bitacoras, asistencias);
        int porcentaje = porcentaje(horasCompletadas, horasRequeridas);
        List<String> motivos = new ArrayList<>();
        if (diasSinActividad != null && diasSinActividad >= 14) motivos.add("Sin actividad reciente");
        if ("en_curso".equalsIgnoreCase(estado) && avanceBajoSegunRitmo(fechaInicio, fechaFin, porcentaje)) {
            motivos.add("Avance bajo de horas");
        }
        if (observadas > 0) motivos.add("Bitácoras con observaciones");
        if (motivos.isEmpty()) return Optional.empty();
        String nivel = (observadas > 0 && diasSinActividad != null && diasSinActividad >= 14)
                || motivos.contains("Avance bajo de horas") && porcentaje < 25
                ? "ALTO" : "MEDIO";
        return Optional.of(new RiesgoReporte(
                proceso, expedienteId, idEstudiante(estudiante), nombreEstudiante(estudiante),
                carrera(estudiante), periodo, entidad, estado, diasSinActividad,
                valor(horasCompletadas), valor(horasRequeridas), porcentaje, (int) observadas, nivel, motivos));
    }

    // Una bitácora observada (rechazada o por corregir) se considera resuelta
    // cuando su parcial tiene una bitácora aprobada posterior (la corrección)
    private long observadasSinResolver(List<Bitacora> bitacoras) {
        return bitacoras.stream()
                .filter(b -> "rechazada".equalsIgnoreCase(b.getEstado())
                        || "requiere_correccion".equalsIgnoreCase(b.getEstado()))
                .filter(b -> bitacoras.stream().noneMatch(otra ->
                        "aprobada".equalsIgnoreCase(otra.getEstado())
                                && Objects.equals(otra.getParcial(), b.getParcial())
                                && esPosterior(otra, b)))
                .count();
    }

    private boolean esPosterior(Bitacora otra, Bitacora base) {
        if (otra.getId() != null && base.getId() != null) return otra.getId() > base.getId();
        return true;
    }

    // El avance bajo solo alerta cuando el expediente ya lleva recorrido (14
    // días) y va por debajo de la mitad del ritmo esperado según sus fechas,
    // para no marcar en riesgo a los recién iniciados
    private boolean avanceBajoSegunRitmo(LocalDate inicio, LocalDate fin, int porcentajeAvance) {
        LocalDate hoy = LocalDate.now();
        if (inicio == null) return false;
        long transcurridos = ChronoUnit.DAYS.between(inicio, hoy);
        if (transcurridos < 14) return false;
        if (fin == null || !fin.isAfter(inicio)) return porcentajeAvance < 50;
        long totales = Math.max(1, ChronoUnit.DAYS.between(inicio, fin));
        int esperado = (int) Math.min(100, (transcurridos * 100) / totales);
        return porcentajeAvance < Math.max(1, esperado / 2);
    }

    private Integer diasSinActividad(LocalDate fechaInicio, List<Bitacora> bitacoras, List<Asistencia> asistencias) {
        Optional<LocalDate> ultimaBitacora = bitacoras.stream().map(Bitacora::getFecha)
                .filter(f -> f != null).max(Comparator.naturalOrder());
        Optional<LocalDate> ultimaAsistencia = asistencias.stream().map(Asistencia::getFecha)
                .filter(f -> f != null).max(Comparator.naturalOrder());
        LocalDate ultima = List.of(ultimaBitacora, ultimaAsistencia, Optional.ofNullable(fechaInicio)).stream()
                .flatMap(Optional::stream).max(Comparator.naturalOrder()).orElse(null);
        return ultima == null ? null : (int) ChronoUnit.DAYS.between(ultima, LocalDate.now());
    }

    private CierreReporte cierre(String proceso, Integer expedienteId, Estudiante estudiante, String periodo,
                                  String entidad, String estado, Integer requeridas, Integer completadas,
                                  LocalDateTime cerradoEn) {
        return new CierreReporte(proceso, expedienteId, idEstudiante(estudiante), nombreEstudiante(estudiante),
                carrera(estudiante), periodo, entidad, estado, valor(requeridas), valor(completadas),
                porcentaje(completadas, requeridas), cerradoEn, cerradoEn != null, valor(completadas) >= valor(requeridas));
    }

    private String csvAsignaciones(List<AsignacionReporte> filas) {
        StringBuilder csv = new StringBuilder("proceso,expediente_id,estudiante_id,estudiante,matricula,carrera,periodo,entidad,estado,fecha_inicio,fecha_fin,horas_requeridas,horas_completadas\n");
        filas.forEach(f -> linea(csv, f.proceso(), f.expedienteId(), f.estudianteId(), f.estudiante(), f.matricula(),
                f.carrera(), f.periodo(), f.entidad(), f.estado(), f.fechaInicio(), f.fechaFin(),
                f.horasRequeridas(), f.horasCompletadas()));
        return csv.toString();
    }

    private String csvCupos(List<CupoReporte> filas) {
        StringBuilder csv = new StringBuilder("proceso,entidad,carrera,periodo,cupos_totales,cupos_usados,cupos_disponibles,ocupacion_porcentaje\n");
        filas.forEach(f -> linea(csv, f.proceso(), f.entidad(), f.carrera(), f.periodo(), f.cuposTotales(),
                f.cuposUsados(), f.cuposDisponibles(), f.ocupacionPorcentaje()));
        return csv.toString();
    }

    private String csvRiesgos(List<RiesgoReporte> filas) {
        StringBuilder csv = new StringBuilder("proceso,expediente_id,estudiante_id,estudiante,carrera,periodo,entidad,estado,dias_sin_actividad,horas_completadas,horas_requeridas,avance_porcentaje,bitacoras_observadas,nivel,motivos\n");
        filas.forEach(f -> linea(csv, f.proceso(), f.expedienteId(), f.estudianteId(), f.estudiante(), f.carrera(),
                f.periodo(), f.entidad(), f.estado(), f.diasSinActividad(), f.horasCompletadas(), f.horasRequeridas(),
                f.avancePorcentaje(), f.bitacorasObservadas(), f.nivel(), String.join("; ", f.motivos())));
        return csv.toString();
    }

    private String csvCierres(List<CierreReporte> filas) {
        StringBuilder csv = new StringBuilder("proceso,expediente_id,estudiante_id,estudiante,carrera,periodo,entidad,estado,horas_requeridas,horas_completadas,cumplimiento_porcentaje,cerrado_en,cerrado,cumple_horas\n");
        filas.forEach(f -> linea(csv, f.proceso(), f.expedienteId(), f.estudianteId(), f.estudiante(), f.carrera(),
                f.periodo(), f.entidad(), f.estado(), f.horasRequeridas(), f.horasCompletadas(),
                f.cumplimientoPorcentaje(), f.cerradoEn(), f.cerrado(), f.cumpleHoras()));
        return csv.toString();
    }

    private void linea(StringBuilder csv, Object... valores) {
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(celda(valores[i]));
        }
        csv.append('\n');
    }

    private String celda(Object valor) {
        if (valor == null) return "";
        String texto = String.valueOf(valor);
        if (!texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0) texto = "'" + texto;
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    private void verificarGestionAcademica(Authentication authentication) {
        boolean permitido = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_COORDINADOR".equals(a.getAuthority()));
        if (!permitido) throw new ResponseStatusException(FORBIDDEN, "Solo gestión académica puede consultar reportes.");
    }

    private String normalizarProceso(String proceso) {
        if (vacio(proceso)) return "TODOS";
        String valor = proceso.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TODOS", PRACTICAS, VINCULACION).contains(valor)) {
            throw new IllegalArgumentException("El proceso debe ser TODOS, PRACTICAS o VINCULACION.");
        }
        return valor;
    }

    private boolean incluye(Authentication authentication, String filtro, String proceso) {
        return ("TODOS".equals(filtro) || proceso.equals(filtro))
                && alcanceCoordinador.procesoVisible(authentication, proceso);
    }

    private boolean visible(Set<String> carreras, Estudiante estudiante) {
        return carreras == null || estudiante != null && carreras.contains(estudiante.getCarrera());
    }

    private boolean visible(Set<String> carreras, String carrera) {
        return carreras == null || carreras.contains(carrera);
    }

    private boolean coincideCarrera(Estudiante estudiante, String filtro) {
        return coincideExacto(carrera(estudiante), filtro);
    }

    private boolean coincideExacto(String valor, String filtro) {
        return vacio(filtro) || valor != null && valor.equalsIgnoreCase(filtro.trim());
    }

    private boolean coincideTexto(String valor, String filtro) {
        return vacio(filtro) || valor != null && normalizar(valor).contains(normalizar(filtro));
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private int porcentaje(Integer completadas, Integer requeridas) {
        int requeridasValor = valor(requeridas);
        if (requeridasValor <= 0) return 0;
        return Math.min(100, Math.round((valor(completadas) * 100f) / requeridasValor));
    }

    private int valor(Integer valor) {
        return valor == null ? 0 : Math.max(0, valor);
    }

    private Integer idEstudiante(Estudiante estudiante) {
        return estudiante == null ? null : estudiante.getId();
    }

    private String matricula(Estudiante estudiante) {
        return estudiante == null ? null : estudiante.getMatricula();
    }

    private String carrera(Estudiante estudiante) {
        return estudiante == null ? null : estudiante.getCarrera();
    }

    private String nombreEstudiante(Estudiante estudiante) {
        if (estudiante == null || estudiante.getUsuario() == null) return "Sin estudiante";
        return (estudiante.getUsuario().getNombre() + " " + estudiante.getUsuario().getApellido()).trim();
    }

    private String nombreEmpresa(Practica practica) {
        return practica.getEmpresa() == null ? "Sin empresa" : practica.getEmpresa().getNombre();
    }

    private String nombreEntidad(Vinculacion vinculacion) {
        String fundacion = vinculacion.getFundacion() == null ? null : vinculacion.getFundacion().getNombre();
        String proyecto = vinculacion.getProyecto() == null ? null : vinculacion.getProyecto().getNombre();
        if (fundacion == null) return proyecto == null ? "Sin entidad" : proyecto;
        return proyecto == null ? fundacion : fundacion + " / " + proyecto;
    }

    private String nombreProyecto(Proyecto proyecto) {
        String fundacion = proyecto.getFundacion() == null ? null : proyecto.getFundacion().getNombre();
        return fundacion == null ? proyecto.getNombre() : fundacion + " / " + proyecto.getNombre();
    }

    private static class CupoAcumulado {
        private final String proceso;
        private final String entidad;
        private final String carrera;
        private final String periodo;
        private int usados;
        private int disponibles;

        private CupoAcumulado(String proceso, String entidad, String carrera, String periodo) {
            this.proceso = proceso;
            this.entidad = entidad;
            this.carrera = carrera;
            this.periodo = periodo;
        }

        private CupoReporte toResponse() {
            int totales = usados + disponibles;
            int ocupacion = totales == 0 ? 0 : Math.min(100, Math.round((usados * 100f) / totales));
            return new CupoReporte(proceso, entidad, carrera, periodo, totales, usados, disponibles, ocupacion);
        }
    }

    public record AsignacionReporte(
            String proceso, Integer expedienteId, Integer estudianteId, String estudiante, String matricula,
            String carrera, String periodo, String entidad, String estado, LocalDate fechaInicio, LocalDate fechaFin,
            Integer horasRequeridas, Integer horasCompletadas) {}

    public record CupoReporte(
            String proceso, String entidad, String carrera, String periodo, Integer cuposTotales,
            Integer cuposUsados, Integer cuposDisponibles, Integer ocupacionPorcentaje) {}

    public record RiesgoReporte(
            String proceso, Integer expedienteId, Integer estudianteId, String estudiante, String carrera,
            String periodo, String entidad, String estado, Integer diasSinActividad, Integer horasCompletadas,
            Integer horasRequeridas, Integer avancePorcentaje, Integer bitacorasObservadas, String nivel,
            List<String> motivos) {}

    public record CierreReporte(
            String proceso, Integer expedienteId, Integer estudianteId, String estudiante, String carrera,
            String periodo, String entidad, String estado, Integer horasRequeridas, Integer horasCompletadas,
            Integer cumplimientoPorcentaje, LocalDateTime cerradoEn, Boolean cerrado, Boolean cumpleHoras) {}
}
