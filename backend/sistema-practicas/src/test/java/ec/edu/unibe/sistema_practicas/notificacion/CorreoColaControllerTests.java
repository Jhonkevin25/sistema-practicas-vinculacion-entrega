package ec.edu.unibe.sistema_practicas.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoColaControllerTests {

    @Mock private CorreoColaRepository correoColaRepository;

    @InjectMocks
    private CorreoColaController controller;

    @Test
    void reintentar_reencola_un_correo_fallido_para_envio_asincrono() {
        CorreoCola correo = correo("FALLIDO");
        LocalDateTime fechaAnterior = correo.getFechaActualizacion();
        when(correoColaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(correo));
        when(correoColaRepository.save(correo)).thenReturn(correo);

        CorreoCola resultado = controller.reintentar(7L);

        assertSame(correo, resultado);
        assertEquals("PENDIENTE", resultado.getEstado());
        assertEquals(0, resultado.getIntentos());
        assertNull(resultado.getUltimoError());
        assertTrue(resultado.getFechaActualizacion().isAfter(fechaAnterior));
        verify(correoColaRepository).save(correo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENVIADO", "PENDIENTE", "PROCESANDO"})
    void reintentar_rechaza_estados_que_no_son_fallidos(String estado) {
        CorreoCola correo = correo(estado);
        when(correoColaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(correo));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.reintentar(7L));

        assertEquals("Solo se pueden reintentar correos en estado FALLIDO.", error.getMessage());
        assertEquals(estado, correo.getEstado());
        assertEquals(3, correo.getIntentos());
        assertEquals("SMTP no disponible", correo.getUltimoError());
        verify(correoColaRepository, never()).save(any(CorreoCola.class));
    }

    @Test
    void reintentar_rechaza_un_correo_inexistente() {
        when(correoColaRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.reintentar(99L));

        assertEquals("No se encontró el correo en la cola.", error.getMessage());
        verify(correoColaRepository, never()).save(any(CorreoCola.class));
    }

    private CorreoCola correo(String estado) {
        CorreoCola correo = new CorreoCola();
        correo.setId(7L);
        correo.setDestinatario("estudiante@unibe.edu.ec");
        correo.setAsunto("Notificación UNIBE");
        correo.setCuerpoHtml("<p>Contenido</p>");
        correo.setEstado(estado);
        correo.setIntentos(3);
        correo.setUltimoError("SMTP no disponible");
        correo.setFechaCreacion(LocalDateTime.now().minusHours(1));
        correo.setFechaActualizacion(LocalDateTime.now().minusMinutes(5));
        return correo;
    }
}
