package ec.edu.unibe.sistema_practicas.paginacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

// Fase 43: envelope uniforme de los listados paginados del API.
// El tamaño se acota a [1, 100] para proteger a la BD de peticiones abusivas.
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static final int TAMANO_MAXIMO = 100;
    public static final int TAMANO_DEFECTO = 20;

    public static Pageable pageable(int pagina, int tamano, Sort orden) {
        return PageRequest.of(Math.max(0, pagina), tamanoValido(tamano), orden);
    }

    public static <T> PaginaResponse<T> desde(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // Pagina una lista ya cargada (usada por los listados que reutilizan la
    // consulta con alcance existente antes de recortar la respuesta).
    public static <T> PaginaResponse<T> deLista(List<T> lista, int pagina, int tamano) {
        int tamanoReal = tamanoValido(tamano);
        int paginaReal = Math.max(0, pagina);
        int total = lista.size();
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamanoReal);
        int desde = Math.min(paginaReal * tamanoReal, total);
        int hasta = Math.min(desde + tamanoReal, total);
        return new PaginaResponse<>(lista.subList(desde, hasta), paginaReal, tamanoReal, total, totalPaginas);
    }

    private static int tamanoValido(int tamano) {
        if (tamano <= 0) return TAMANO_DEFECTO;
        return Math.min(tamano, TAMANO_MAXIMO);
    }
}
