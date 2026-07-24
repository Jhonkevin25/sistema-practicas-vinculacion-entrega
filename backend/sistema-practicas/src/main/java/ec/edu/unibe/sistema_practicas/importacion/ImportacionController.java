package ec.edu.unibe.sistema_practicas.importacion;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/importaciones")
@RequiredArgsConstructor
public class ImportacionController {

    private static final Logger log = LoggerFactory.getLogger(ImportacionController.class);

    private final CsvInstitucionalComponent csvInstitucionalComponent;
    private final ImportacionInstitucionalComponent importacionInstitucionalComponent;
    private final ImportacionRepository importacionRepository;
    private final ObjectMapper objectMapper;
    private final AuditoriaEmitter auditoriaEmitter;

    @GetMapping("/paginado")
    public PaginaResponse<Importacion> obtenerPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado) {
        if (size > 100) size = 100;
        Pageable pageable = PageRequest.of(page, size, Sort.by("fecha").descending());
        String tipoFiltro = tipo != null && tipo.isBlank() ? null : tipo;
        String estadoFiltro = estado != null && estado.isBlank() ? null : estado;
        Page<Importacion> pagina = importacionRepository.findByFiltros(tipoFiltro, estadoFiltro, pageable);
        return new PaginaResponse<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }

    @PostMapping(value = "/estudiantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumenImportacion importarEstudiantes(@RequestPart("archivo") MultipartFile archivo,
                                                   @AuthenticationPrincipal Usuario administrador) {
        return procesar(archivo, "ESTUDIANTES", administrador,
                fila -> importacionInstitucionalComponent.importarEstudiante(mapearEstudiante(fila)));
    }

    @PostMapping(value = "/notas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumenImportacion importarNotas(@RequestPart("archivo") MultipartFile archivo,
                                             @AuthenticationPrincipal Usuario administrador) {
        return procesar(archivo, "NOTAS", administrador,
                fila -> importacionInstitucionalComponent.importarNota(mapearNota(fila), administrador));
    }

    private ResumenImportacion procesar(MultipartFile archivo,
                                         String tipo,
                                         Usuario administrador,
                                         ProcesadorFila procesador) {
        if (administrador == null || administrador.getId() == null) {
            throw new IllegalArgumentException("No se pudo identificar al administrador que realiza la importación.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV es requerido.");
        }
        if (archivo.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 2MB.");
        }
        String originalFilename = archivo.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .csv.");
        }
        String nombreArchivo = nombreSeguro(originalFilename);
        List<FilaCsv> filas;
        try {
            filas = csvInstitucionalComponent.leer(archivo);
        } catch (IllegalArgumentException e) {
            registrarImportacion(nombreArchivo, tipo, administrador, 0, 0, 0, 0, 0, 0,
                    "FALLIDA", List.of(new ErrorFilaImportacion(0, nombreArchivo, e.getMessage())));
            throw e;
        }

        int creados = 0;
        int actualizados = 0;
        int enlazados = 0;
        int filasOk = 0;
        List<ErrorFilaImportacion> errores = new ArrayList<>();

        for (FilaCsv fila : filas) {
            try {
                AccionImportacion accion = procesador.procesar(fila);
                filasOk++;
                if (accion == AccionImportacion.CREADO) creados++;
                if (accion == AccionImportacion.ACTUALIZADO) actualizados++;
                if (accion == AccionImportacion.ENLAZADO) enlazados++;
            } catch (Exception e) {
                String identificador = valorPrimero(fila, "external_id", "id_externo",
                        "email_institucional", "correo_institucional", "email");
                errores.add(new ErrorFilaImportacion(
                        fila.numero(),
                        identificador.isBlank() ? "Sin identificador" : identificador,
                        mensajeSeguro(e)
                ));
            }
        }

        int filasError = errores.size();
        String estado = filasError == 0 ? "COMPLETADA" : (filasOk == 0 ? "FALLIDA" : "CON_ERRORES");
        Importacion importacion = registrarImportacion(
                nombreArchivo,
                tipo,
                administrador,
                filas.size(),
                filasOk,
                filasError,
                creados,
                actualizados,
                enlazados,
                estado,
                errores
        );
        return new ResumenImportacion(
                importacion.getId(),
                nombreArchivo,
                tipo,
                estado,
                filas.size(),
                filasOk,
                filasError,
                creados,
                actualizados,
                enlazados,
                List.copyOf(errores)
        );
    }

    private FilaEstudianteInstitucional mapearEstudiante(FilaCsv fila) {
        String externalId = requerido(fila, "external_id", "id_externo");
        String cedula = requerido(fila, "cedula", "identificacion");
        String email = requerido(fila, "email_institucional", "correo_institucional", "email", "correo");
        String nombre = requerido(fila, "nombre", "nombres");
        String apellido = requerido(fila, "apellido", "apellidos");
        String carrera = requerido(fila, "carrera");
        int semestre = entero(requerido(fila, "semestre"), "semestre");
        if (semestre < 1 || semestre > 20) {
            throw new IllegalArgumentException("El semestre debe estar entre 1 y 20.");
        }
        String estadoMatricula = requerido(fila, "estado_matricula", "estado");
        boolean activa = matriculaActiva(estadoMatricula);
        String matricula = valorPrimero(fila, "matricula", "codigo_estudiante");
        String periodo = valorPrimero(fila, "periodo_academico", "periodo");
        return new FilaEstudianteInstitucional(
                externalId, cedula, email, nombre, apellido, matricula, carrera, semestre, periodo, activa);
    }

    private FilaNotaInstitucional mapearNota(FilaCsv fila) {
        String externalId = valorPrimero(fila, "external_id", "id_externo");
        String email = valorPrimero(fila, "email_institucional", "correo_institucional", "email", "correo");
        if (externalId.isBlank() && email.isBlank()) {
            throw new IllegalArgumentException("La nota requiere external_id o correo institucional.");
        }
        String periodo = requerido(fila, "periodo_academico", "periodo");
        String semestreTexto = valorPrimero(fila, "semestre");
        Integer semestre = semestreTexto.isBlank() ? null : entero(semestreTexto, "semestre");
        if (semestre != null && (semestre < 1 || semestre > 20)) {
            throw new IllegalArgumentException("El semestre debe estar entre 1 y 20.");
        }
        double promedio = decimal(requerido(fila, "promedio", "nota_promedio"), "promedio");
        if (promedio < 0 || promedio > 10) {
            throw new IllegalArgumentException("El promedio debe estar entre 0 y 10.");
        }
        return new FilaNotaInstitucional(externalId, email, periodo, semestre, promedio);
    }

    private Importacion registrarImportacion(String archivo,
                                              String tipo,
                                              Usuario usuario,
                                              int total,
                                              int ok,
                                              int error,
                                              int creados,
                                              int actualizados,
                                              int enlazados,
                                              String estado,
                                              List<ErrorFilaImportacion> errores) {
        Importacion importacion = new Importacion();
        importacion.setArchivoNombre(archivo);
        importacion.setTipo(tipo);
        importacion.setFilasTotal(total);
        importacion.setFilasOk(ok);
        importacion.setFilasError(error);
        importacion.setCreados(creados);
        importacion.setActualizados(actualizados);
        importacion.setEnlazados(enlazados);
        importacion.setEstado(estado);
        importacion.setDetalleErrores(serializarErrores(errores));
        importacion.setUsuario(usuario);
        importacion.setFecha(LocalDateTime.now());
        Importacion guardada = importacionRepository.save(importacion);
        auditoriaEmitter.registrar("IMPORTACIONES", "IMPORTACION_INSTITUCIONAL", usuario,
                null, snapshotImportacion(guardada));
        return guardada;
    }

    private Map<String, Object> snapshotImportacion(Importacion importacion) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", importacion.getId());
        datos.put("archivo", importacion.getArchivoNombre());
        datos.put("tipo", importacion.getTipo());
        datos.put("estado", importacion.getEstado());
        datos.put("filasTotal", importacion.getFilasTotal());
        datos.put("filasOk", importacion.getFilasOk());
        datos.put("filasError", importacion.getFilasError());
        datos.put("creados", importacion.getCreados());
        datos.put("actualizados", importacion.getActualizados());
        datos.put("enlazados", importacion.getEnlazados());
        return datos;
    }

    private String serializarErrores(List<ErrorFilaImportacion> errores) {
        try {
            return objectMapper.writeValueAsString(errores);
        } catch (Exception e) {
            log.warn("No se pudo serializar el detalle de errores de importación: {}", e.getMessage());
            return "[]";
        }
    }

    private String requerido(FilaCsv fila, String... columnas) {
        String valor = valorPrimero(fila, columnas);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Falta la columna o valor obligatorio: " + columnas[0] + ".");
        }
        return valor;
    }

    private String valorPrimero(FilaCsv fila, String... columnas) {
        for (String columna : columnas) {
            String valor = fila.valor(columna);
            if (!valor.isBlank()) return valor;
        }
        return "";
    }

    private int entero(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo " + campo + " debe ser un número entero.");
        }
    }

    private double decimal(String valor, String campo) {
        try {
            return Double.parseDouble(valor.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo " + campo + " debe ser numérico.");
        }
    }

    private boolean matriculaActiva(String valor) {
        String estado = valor.trim().toUpperCase(Locale.ROOT);
        if (Set.of("ACTIVO", "ACTIVA", "MATRICULADO", "MATRICULADA", "SI", "TRUE", "1").contains(estado)) {
            return true;
        }
        if (Set.of("INACTIVO", "INACTIVA", "RETIRADO", "RETIRADA", "EGRESADO", "EGRESADA", "NO", "FALSE", "0")
                .contains(estado)) {
            return false;
        }
        throw new IllegalArgumentException("El estado_matricula no es válido.");
    }

    private String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) return "archivo.csv";
        String normalizado = nombre.replace('\\', '/');
        int separador = normalizado.lastIndexOf('/');
        String base = separador >= 0 ? normalizado.substring(separador + 1) : normalizado;
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private String mensajeSeguro(Exception e) {
        if (e instanceof IllegalArgumentException && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        log.warn("Fallo inesperado al procesar una fila de importación: {}", e.getMessage());
        return "No se pudo procesar la fila por un conflicto de datos.";
    }

    @FunctionalInterface
    private interface ProcesadorFila {
        AccionImportacion procesar(FilaCsv fila);
    }
}
