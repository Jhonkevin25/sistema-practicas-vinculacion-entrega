package ec.edu.unibe.sistema_practicas.auditoria;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaEmitterTests {

    @Mock private AuditoriaRepository auditoriaRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private AuditoriaEmitter auditoriaEmitter;

    @Test
    void registra_snapshots_json_con_usuario_y_fecha() {
        Usuario usuario = new Usuario();
        usuario.setId(9);
        when(objectMapper.writeValueAsString(Map.of("valor", 1))).thenReturn("{\"valor\":1}");
        when(objectMapper.writeValueAsString(Map.of("valor", 2))).thenReturn("{\"valor\":2}");
        when(auditoriaRepository.save(any(Auditoria.class))).thenAnswer(inv -> inv.getArgument(0));

        auditoriaEmitter.registrar("practicas", "cambio_tutor", usuario,
                Map.of("valor", 1), Map.of("valor", 2));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        Auditoria guardada = captor.getValue();
        assertEquals("PRACTICAS", guardada.getTablaAfectada());
        assertEquals("CAMBIO_TUTOR", guardada.getAccion());
        assertEquals("{\"valor\":1}", guardada.getDatosAntes());
        assertEquals("{\"valor\":2}", guardada.getDatosDespues());
        assertEquals(usuario, guardada.getUsuario());
        assertNotNull(guardada.getFecha());
    }
}
