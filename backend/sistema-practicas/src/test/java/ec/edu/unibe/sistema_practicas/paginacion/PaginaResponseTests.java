package ec.edu.unibe.sistema_practicas.paginacion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginaResponseTests {

    @Test
    void deLista_recorta_la_pagina_solicitada_con_totales_correctos() {
        List<Integer> lista = IntStream.rangeClosed(1, 45).boxed().toList();

        PaginaResponse<Integer> pagina = PaginaResponse.deLista(lista, 1, 20);

        assertEquals(List.of(21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32, 33, 34, 35, 36, 37, 38, 39, 40), pagina.contenido());
        assertEquals(45, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(1, pagina.pagina());
    }

    @Test
    void deLista_devuelve_vacio_fuera_de_rango_sin_fallar() {
        List<Integer> lista = List.of(1, 2, 3);

        PaginaResponse<Integer> pagina = PaginaResponse.deLista(lista, 9, 20);

        assertTrue(pagina.contenido().isEmpty());
        assertEquals(3, pagina.totalElementos());
        assertEquals(1, pagina.totalPaginas());
    }

    @Test
    void tamano_se_acota_a_los_limites_definidos() {
        List<Integer> lista = IntStream.rangeClosed(1, 300).boxed().toList();

        assertEquals(PaginaResponse.TAMANO_DEFECTO,
                PaginaResponse.deLista(lista, 0, 0).tamano());
        assertEquals(PaginaResponse.TAMANO_MAXIMO,
                PaginaResponse.deLista(lista, 0, 5000).tamano());
        assertEquals(PaginaResponse.TAMANO_MAXIMO,
                PaginaResponse.deLista(lista, 0, 5000).contenido().size());
    }
}
