package ec.edu.unibe.sistema_practicas.bitacora;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlujoBitacoraTests {

    @Test
    void correccion_sin_reenvio_permanece_abierta() {
        assertTrue(FlujoBitacora.tieneAbiertas(List.of(bitacora(2, 1, "requiere_correccion")), 1));
    }

    @Test
    void aprobada_posterior_resuelve_correccion_historica_del_mismo_parcial() {
        List<Bitacora> bitacoras = List.of(
                bitacora(1, 1, "requiere_correccion"),
                bitacora(2, 1, "aprobada"));

        assertFalse(FlujoBitacora.tieneAbiertas(bitacoras, 1));
    }

    @Test
    void rechazada_es_historial_y_no_bloquea_el_cierre() {
        assertFalse(FlujoBitacora.tieneAbiertas(List.of(bitacora(1, 1, "rechazada"))));
    }

    @Test
    void pendiente_siempre_bloquea() {
        assertTrue(FlujoBitacora.tieneAbiertas(List.of(bitacora(1, 1, "pendiente"))));
    }

    private Bitacora bitacora(int id, int parcial, String estado) {
        Bitacora bitacora = new Bitacora();
        bitacora.setId(id);
        bitacora.setParcial(parcial);
        bitacora.setEstado(estado);
        return bitacora;
    }
}
