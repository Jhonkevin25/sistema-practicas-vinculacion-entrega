package ec.edu.unibe.sistema_practicas.reporte;

import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;

import java.util.List;

// Fase 55: envelope de paginación propio de Reportes. A diferencia de
// PaginaResponse<T> (genérico, reutilizado por todos los listados del
// sistema), este agrega valorPrincipal/valorSecundario calculados sobre el
// universo filtrado COMPLETO (antes de recortar la página), para que los KPIs
// del frontend no queden calculados solo con las filas de la página visible.
public record ReportePaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        long valorPrincipal,
        long valorSecundario) {

    public static <T> ReportePaginaResponse<T> deLista(
            List<T> lista, int pagina, int tamano, long valorPrincipal, long valorSecundario) {
        PaginaResponse<T> base = PaginaResponse.deLista(lista, pagina, tamano);
        return new ReportePaginaResponse<>(
                base.contenido(), base.pagina(), base.tamano(), base.totalElementos(), base.totalPaginas(),
                valorPrincipal, valorSecundario);
    }
}
