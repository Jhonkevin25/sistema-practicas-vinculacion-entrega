package ec.edu.unibe.sistema_practicas.notaacademica;

import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.coordinador.AlcanceCoordinador;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionEmitter;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaAcademicaControllerTests {

    @Mock private NotaAcademicaRepository notaAcademicaRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private AlcanceCoordinador alcanceCoordinador;
    @Mock private NotificacionEmitter notificacionEmitter;
    @Mock private AuditoriaEmitter auditoriaEmitter;

    @InjectMocks
    private NotaAcademicaController controller;

    @Test
    void estudiante_carga_nota_academica_con_auditoria_servidor() {
        Usuario usuario = new Usuario();
        usuario.setId(4);
        usuario.setEmail("estudiante@unibe.edu.ec");
        Estudiante estudiante = new Estudiante();
        estudiante.setId(8);
        estudiante.setUsuario(usuario);
        estudiante.setCarrera("Software");
        NotaAcademica nota = new NotaAcademica();
        nota.setSemestre(4);
        nota.setPeriodoAcademico("2025-2");
        nota.setPromedio(9.1);
        when(estudianteRepository.findByUsuarioEmail(usuario.getEmail())).thenReturn(Optional.of(estudiante));
        when(notaAcademicaRepository.save(any(NotaAcademica.class))).thenAnswer(inv -> {
            NotaAcademica guardada = inv.getArgument(0);
            guardada.setId(30);
            return guardada;
        });

        controller.create(nota,
                new UsernamePasswordAuthenticationToken(usuario, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))), usuario);

        verify(auditoriaEmitter).registrar(eq("NOTAS_ACADEMICAS"), eq("CARGAR_NOTA_ACADEMICA"),
                eq(usuario), eq(null), anyMap());
    }
}
