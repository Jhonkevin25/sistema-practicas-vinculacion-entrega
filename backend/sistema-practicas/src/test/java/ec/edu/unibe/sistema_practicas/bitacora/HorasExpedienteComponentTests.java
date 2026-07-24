package ec.edu.unibe.sistema_practicas.bitacora;

import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorasExpedienteComponentTests {

    @Mock private BitacoraRepository bitacoraRepository;

    @InjectMocks
    private HorasExpedienteComponent component;

    @Test
    void practica_acredita_solo_horas_aprobadas_hasta_el_limite() {
        Practica practica = new Practica();
        practica.setId(10);
        practica.setHorasRequeridas(240);
        practica.setHorasCompletadas(99);
        when(bitacoraRepository.sumHorasAprobadasByPracticaId(10)).thenReturn(260);

        assertEquals(240, component.aplicarHorasAprobadas(practica));
        assertEquals(240, practica.getHorasCompletadas());
    }

    @Test
    void vinculacion_usa_la_suma_aprobada_del_expediente_exacto() {
        Vinculacion vinculacion = new Vinculacion();
        vinculacion.setId(20);
        vinculacion.setHorasRequeridas(160);
        when(bitacoraRepository.sumHorasAprobadasByVinculacionId(20)).thenReturn(72);

        assertEquals(72, component.aplicarHorasAprobadas(vinculacion));
        assertEquals(72, vinculacion.getHorasCompletadas());
    }

    @Test
    void expediente_sin_horas_requeridas_validas_se_rechaza() {
        Practica practica = new Practica();
        practica.setId(10);
        practica.setHorasRequeridas(0);

        assertThrows(IllegalArgumentException.class,
                () -> component.aplicarHorasAprobadas(practica));
        verifyNoInteractions(bitacoraRepository);
    }
}
