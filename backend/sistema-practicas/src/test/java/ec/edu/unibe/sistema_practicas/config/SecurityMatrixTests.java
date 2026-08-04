package ec.edu.unibe.sistema_practicas.config;

import ec.edu.unibe.sistema_practicas.config.JwtTokenProvider;
import ec.edu.unibe.sistema_practicas.coordinador.CoordinadorCarreraRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.Notificacion;
import ec.edu.unibe.sistema_practicas.notificacion.NotificacionRepository;
import ec.edu.unibe.sistema_practicas.practica.Practica;
import ec.edu.unibe.sistema_practicas.practica.PracticaRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import ec.edu.unibe.sistema_practicas.vinculacion.Vinculacion;
import ec.edu.unibe.sistema_practicas.vinculacion.VinculacionRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fase 9: matriz de autorizacion por rol sobre las rutas del API.
// Solo se ejercitan negaciones y GETs permitidos (solo lectura), para no
// escribir en la base de Supabase. La mayoria de las negaciones cortan en la
// matriz (403 antes del controlador); las de autorizacion horizontal llegan
// al controlador pero fallan antes de cualquier save.
// Las anotaciones (incl. properties) deben ser identicas a las de
// SistemaPracticasApplicationTests para compartir un solo contexto y no
// agotar las 15 conexiones del pooler de Supabase.
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=1"
})
@AutoConfigureMockMvc
class SecurityMatrixTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PracticaRepository practicaRepository;

    @Autowired
    private VinculacionRepository vinculacionRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private CoordinadorCarreraRepository coordinadorCarreraRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // ---------- Sin autenticacion ----------

    @Test
    void anonimo_no_accede_a_rutas_protegidas() throws Exception {
        mvc.perform(get("/api/practicas")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/usuarios")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/practicas/1/acta")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/practicas/1/linea-tiempo")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/convenios")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/importaciones/estudiantes")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/reportes/asignaciones")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/correos/1/reintentar")).andExpect(status().is4xxClientError());
        mvc.perform(patch("/api/usuarios/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nuevo@unibe.edu.ec\"}"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void ruta_no_declarada_se_rechaza_aunque_haya_sesion() throws Exception {
        mvc.perform(get("/api/ruta-no-declarada").with(user("a").roles("ADMIN")))
           .andExpect(status().isForbidden());
    }

    @Test
    void recuperacion_password_es_publica_y_no_revela_correo() throws Exception {
        mvc.perform(post("/api/auth/recuperar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-existe-pravi@sistema.edu.ec\"}"))
           .andExpect(status().isOk());

        mvc.perform(post("/api/auth/restablecer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token-invalido\",\"nuevaPassword\":\"Nueva2026\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void health_check_es_publico_y_el_resto_de_actuator_queda_cerrado() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/env").with(user("a").roles("ADMIN")))
           .andExpect(status().isForbidden());
    }

    @Test
    void anonimo_no_cambia_password() throws Exception {
        mvc.perform(post("/api/auth/cambiar-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"Actual2026\",\"nuevaPassword\":\"Nueva2026\"}"))
           .andExpect(status().is4xxClientError());
    }

    // ---------- ESTUDIANTE ----------

    @Test
    void estudiante_no_lista_usuarios_ni_estudiantes() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/estudiantes").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_ve_auditoria_ni_alcance() throws Exception {
        mvc.perform(get("/api/auditoria?tabla=POSTULACIONES_MERITOCRATICAS")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/coordinadores/alcance/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_escribe_catalogos_ni_expedientes() throws Exception {
        mvc.perform(post("/api/vacantes").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/practicas").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/evaluaciones").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/fechas").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/convenios").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/importaciones/estudiantes").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/asignaciones").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_lee_vacantes_practicas_y_sus_favoritos() throws Exception {
        mvc.perform(get("/api/vacantes").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/practicas").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/favoritos/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
    }

    @Test
    void estudiante_consulta_solo_sus_recursos_personales() throws Exception {
        mvc.perform(get("/api/practicas/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/postulaciones/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/postulaciones/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/bitacoras/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/asistencias/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(post("/api/asistencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/practicas/me/linea-tiempo").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/me/linea-tiempo").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
    }

    // ---------- TUTOR ----------

    @Test
    void tutor_no_lista_usuarios_ni_postula_ni_publica_vacantes() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/postulaciones").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/postulaciones").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vacantes").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/fechas").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_configura_alcance_de_coordinador() throws Exception {
        mvc.perform(get("/api/coordinadores/alcance/me").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/convenios").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/importaciones/notas").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/riesgos").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_registra_bitacoras() throws Exception {
        mvc.perform(post("/api/bitacoras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_lee_estudiantes_practicas_y_bitacoras() throws Exception {
        mvc.perform(get("/api/estudiantes").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/practicas").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/bitacoras").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
    }

    @Test
    void tutor_no_crea_notas_coordinacion() throws Exception {
        mvc.perform(post("/api/notas-coordinacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_lee_notas_coordinacion_propias() throws Exception {
        mvc.perform(get("/api/notas-coordinacion/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
    }

    // Nota: no se prueba el caso ADMIN->200 aqui porque ejecutaria de verdad
    // revisarAtrasos() contra la Supabase real (podria notificar/enviar
    // correos a estudiantes reales en cada corrida de build). Solo se
    // verifica la autorizacion, que no tiene ese efecto colateral.
    @Test
    void coordinador_no_ejecuta_alerta_atraso() throws Exception {
        mvc.perform(post("/api/seguimiento/alertas-atraso/ejecutar").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_ejecuta_alerta_atraso() throws Exception {
        mvc.perform(post("/api/seguimiento/alertas-atraso/ejecutar").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_lee_notas_coordinacion_de_su_tutorado() throws Exception {
        Practica practica = practicaRepository.findAll().stream()
                .filter(p -> p.getEstudiante() != null && p.getTutor() != null)
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(practica != null,
                "No hay practicas con tutor asignado para probar notas de coordinacion");

        mvc.perform(get("/api/notas-coordinacion/estudiante/" + practica.getEstudiante().getId())
                .with(usuario(practica.getTutor(), "TUTOR")))
           .andExpect(status().isOk());
    }

    @Test
    void tutor_consulta_solo_sus_tutorias_personales() throws Exception {
        mvc.perform(get("/api/practicas/tutor/me").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/tutor/me").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/practicas/tutor/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/vinculacion/tutor/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_modifica_directamente_expedientes() throws Exception {
        mvc.perform(put("/api/practicas/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(put("/api/vinculacion/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    // ---------- COORDINADOR ----------

    @Test
    void coordinador_no_administra_usuarios() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/fechas").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(delete("/api/configuracion/fechas/1").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/convenios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/ofertas-cupos-empresa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/tutores-empresa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/tutores-fundacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/importaciones/estudiantes").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_no_crea_ni_elimina_estudiantes_ni_lista_candidatos() throws Exception {
        mvc.perform(post("/api/estudiantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(delete("/api/estudiantes/1").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/candidatos-estudiante").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_no_cambia_matricula_carrera_ni_usuario_del_estudiante() throws Exception {
        // Con cualquier coordinador real y cualquier estudiante, cambiar la
        // identidad institucional via PUT debe terminar en 403: o el
        // estudiante esta fuera de su alcance, o el controlador rechaza el
        // cambio de matricula antes de cualquier save.
        Usuario coordinador = coordinadorCarreraRepository.findAll().stream()
                .map(fila -> fila.getUsuario())
                .findFirst()
                .orElse(null);
        Estudiante estudiante = estudianteRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(coordinador != null && estudiante != null,
                "No hay coordinador o estudiantes para probar la autorizacion horizontal");

        String cambioIdentidad = """
                {
                  "matricula": "MAT-CAMBIO-NO-PERMITIDO",
                  "carrera": "%s",
                  "semestre": 1,
                  "periodoAcademico": "2026-1"
                }
                """.formatted(estudiante.getCarrera());
        mvc.perform(put("/api/estudiantes/" + estudiante.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cambioIdentidad)
                .with(usuario(coordinador, "COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_no_configura_su_alcance_ni_el_de_otro_usuario() throws Exception {
        String alcance = """
                {
                  "tipo": "AMBOS",
                  "carreras": ["Derecho"]
                }
                """;

        mvc.perform(put("/api/coordinadores/alcance/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(alcance)
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(put("/api/coordinadores/1/alcance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(alcance)
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_consulta_convenios() throws Exception {
        mvc.perform(get("/api/convenios").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
    }

    @Test
    void ofertas_de_fundacion_solo_gestion_consulta_y_solo_admin_escribe() throws Exception {
        Usuario coordinadorVinculacion = coordinadorCarreraRepository.findAll().stream()
                .filter(fila -> "VINCULACION".equalsIgnoreCase(fila.getCoordinacionTipo())
                        || "AMBOS".equalsIgnoreCase(fila.getCoordinacionTipo()))
                .map(fila -> fila.getUsuario())
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(coordinadorVinculacion != null,
                "No hay coordinador con alcance de Vinculación para probar la consulta");
        mvc.perform(get("/api/ofertas-cupos-fundacion")
                .with(usuario(coordinadorVinculacion, "COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/ofertas-cupos-fundacion")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/ofertas-cupos-fundacion")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/ofertas-cupos-fundacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/ofertas-cupos-fundacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void periodos_son_consultables_por_roles_mvp_y_configurables_solo_por_admin() throws Exception {
        mvc.perform(get("/api/configuracion/periodos").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/configuracion/periodos").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/configuracion/periodos").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/configuracion/periodos").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(post("/api/configuracion/periodos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/periodos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void coordinador_solo_lee_directorio_de_tutores_y_no_auditoria() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/tutores").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/auditoria?tabla=POSTULACIONES_MERITOCRATICAS")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria?tabla=IMPORTACIONES")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/auditoria")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tablaAfectada\":\"PRACTICAS\",\"accion\":\"FALSA\"}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_consulta_y_exporta_reportes() throws Exception {
        mvc.perform(get("/api/reportes/asignaciones").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/cupos").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/riesgos").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/cierres").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/conteos-overview").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/distribucion-estados").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/cupos-top-entidades").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/exportar?tipo=ASIGNACIONES")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
    }

    @Test
    void auditoria_de_tablas_fuera_de_lista_blanca_se_rechaza() throws Exception {
        mvc.perform(get("/api/auditoria?tabla=USUARIOS").with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
    }

    // ---------- ADMIN ----------

    @Test
    void admin_consulta_candidatos_a_estudiante() throws Exception {
        mvc.perform(get("/api/usuarios/candidatos-estudiante").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
    }

    @Test
    void admin_lista_usuarios_y_estudiantes() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/tutores").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/estudiantes").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/ofertas-cupos-empresa").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/auditoria?tabla=POSTULACIONES_MERITOCRATICAS")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
    }

    @Test
    void admin_no_es_bloqueado_al_configurar_alcance_de_coordinador() throws Exception {
        mvc.perform(put("/api/coordinadores/2147483647/alcance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tipo": "AMBOS",
                          "carreras": ["Derecho"]
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void admin_no_es_bloqueado_por_seguridad_al_escribir_configuracion() throws Exception {
        mvc.perform(post("/api/configuracion/fechas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void admin_no_es_bloqueado_por_seguridad_al_importar_csv() throws Exception {
        mvc.perform(multipart("/api/importaciones/estudiantes")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mvc.perform(multipart("/api/importaciones/notas")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void admin_consulta_reportes() throws Exception {
        mvc.perform(get("/api/reportes/asignaciones").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/conteos-overview").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/distribucion-estados").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/cupos-top-entidades").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/exportar?tipo=CIERRES").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
    }

    @Test
    void admin_no_inserta_auditoria_desde_el_cliente() throws Exception {
        mvc.perform(post("/api/auditoria")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tablaAfectada\":\"PRACTICAS\",\"accion\":\"FALSA\"}")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isForbidden());
    }

    @Test
    void admin_gestiona_correos_fallidos() throws Exception {
        mvc.perform(get("/api/correos/paginado").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
    }

    @Test
    void admin_puede_solicitar_reintento_de_correo() throws Exception {
        mvc.perform(post("/api/correos/9223372036854775807/reintentar")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("No se encontró el correo en la cola."));
    }

    @Test
    void no_admin_no_gestiona_correos() throws Exception {
        mvc.perform(get("/api/correos/paginado").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/correos/paginado").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/correos/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/correos/1/reintentar").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/correos/1/reintentar").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/correos/1/reintentar").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_consolida_postulacion_en_practica() throws Exception {
        mvc.perform(post("/api/postulaciones/1/consolidar-practica")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_procesa_meritocracia() throws Exception {
        mvc.perform(post("/api/postulaciones/procesar-meritocracia")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_accede_bandeja_seguimiento_coordinador() throws Exception {
        mvc.perform(get("/api/practicas/seguimiento").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/vinculacion/seguimiento").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/practicas/seguimiento/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/vinculacion/seguimiento/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_aprueba_postulacion_vinculacion() throws Exception {
        mvc.perform(post("/api/vinculacion/postulaciones/1/aprobar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_cierra_expedientes_academicos() throws Exception {
        mvc.perform(post("/api/practicas/1/cerrar")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vinculacion/1/cerrar")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_accede_bandeja_seguimiento_coordinador() throws Exception {
        mvc.perform(get("/api/practicas/seguimiento").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/vinculacion/seguimiento").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/practicas/seguimiento/paginado").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/vinculacion/seguimiento/paginado").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_accede_postulaciones_vinculacion() throws Exception {
        mvc.perform(get("/api/vinculacion/postulaciones").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_cierra_expedientes_academicos() throws Exception {
        mvc.perform(post("/api/practicas/1/cerrar")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vinculacion/1/cerrar")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void vinculacion_cerrada_rechaza_edicion_normal_sin_justificacion_admin() throws Exception {
        Vinculacion vinculacionCerrada = vinculacionRepository.findAll().stream()
                .filter(v -> "completado".equalsIgnoreCase(v.getEstado()))
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(vinculacionCerrada != null, "No hay vinculaciones cerradas para probar bloqueo");

        mvc.perform(put("/api/vinculacion/" + vinculacionCerrada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"horasCompletadas\":1}")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void estudiante_no_verifica_notas_academicas() throws Exception {
        mvc.perform(put("/api/notas-academicas/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"VERIFICADO\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_no_administra_catalogo_carreras() throws Exception {
        mvc.perform(post("/api/carreras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\"}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_y_tutor_no_consultan_catalogos_administrativos() throws Exception {
        mvc.perform(get("/api/carreras").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/carreras").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/ofertas-cupos-empresa").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/ofertas-cupos-empresa").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_y_tutor_no_finalizan_excepcionalmente() throws Exception {
        mvc.perform(post("/api/practicas/1/finalizar-excepcional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RETIRADO\",\"motivo\":\"Motivo suficiente\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vinculacion/1/finalizar-excepcional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RETIRADO\",\"motivo\":\"Motivo suficiente\"}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void gestion_academica_tiene_ruta_de_finalizacion_excepcional() throws Exception {
        mvc.perform(post("/api/practicas/1/finalizar-excepcional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vinculacion/1/finalizar-excepcional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isBadRequest());
    }

    // Fase 42: la liberacion del cupo retirado es exclusiva de gestion academica
    @Test
    void estudiante_y_tutor_no_liberan_cupos_retirados() throws Exception {
        mvc.perform(post("/api/vinculacion/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Motivo suficiente\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vinculacion/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Motivo suficiente\"}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/practicas/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Motivo suficiente\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/practicas/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Motivo suficiente\"}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void gestion_academica_tiene_ruta_de_liberacion_de_cupo() throws Exception {
        mvc.perform(post("/api/vinculacion/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mvc.perform(post("/api/vinculacion/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mvc.perform(post("/api/practicas/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mvc.perform(post("/api/practicas/1/liberar-cupo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    // Fase 42: el cierre formal del periodo es exclusivo de ADMIN.
    // El caso ADMIN usa un id inexistente: valida la matriz (no-403) sin
    // riesgo de cerrar de verdad un periodo de la BD real (cierre irreversible).
    @Test
    void solo_admin_cierra_periodos_academicos() throws Exception {
        mvc.perform(post("/api/configuracion/periodos/2147483647/cerrar")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/periodos/2147483647/cerrar")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/configuracion/periodos/2147483647/cerrar")
                .with(user("a").roles("ADMIN")))
           .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    // Fase 43: los listados paginados heredan la matriz de sus recursos
    @Test
    void listados_paginados_respetan_la_matriz_por_recurso() throws Exception {
        mvc.perform(get("/api/usuarios/paginado").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/paginado").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/estudiantes/paginado").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/estudiantes/paginado").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/estudiantes/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/empresas/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/fundaciones/paginado").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/proyectos/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vacantes/paginado").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/practicas/paginado").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/paginado").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios/paginado").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
    }

    // Fase 42: editar/pausar/reactivar vacantes queda en gestion academica
    @Test
    void estudiante_y_tutor_no_editan_ni_pausan_vacantes() throws Exception {
        mvc.perform(put("/api/vacantes/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vacantes/1/pausar").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/vacantes/1/reactivar").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void todos_los_roles_consultan_linea_de_tiempo_personal_o_de_gestion() throws Exception {
        mvc.perform(get("/api/practicas/me/linea-tiempo").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/vinculacion/me/linea-tiempo").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/practicas/me/linea-tiempo").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
    }

    @Test
    void estudiante_no_registra_notas_academicas() throws Exception {
        // Las notas oficiales entran por importacion institucional o por
        // gestion academica; el estudiante no las digita.
        mvc.perform(post("/api/notas-academicas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_revisa_bitacoras() throws Exception {
        mvc.perform(put("/api/bitacoras/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"aprobada\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutores_externos_solo_son_consultables_por_gestion_y_configurables_por_admin() throws Exception {
        mvc.perform(get("/api/tutores-empresa/empresa/1"))
           .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/tutores-empresa/empresa/1").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/tutores-empresa/empresa/1").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/tutores-empresa/empresa/2147483647").with(user("a").roles("ADMIN")))
           .andExpect(status().isNotFound());
    }

    @Test
    void tutores_externos_de_fundacion_solo_son_consultables_por_gestion_y_configurables_por_admin() throws Exception {
        mvc.perform(get("/api/tutores-fundacion/fundacion/1"))
           .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/tutores-fundacion/fundacion/1").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/tutores-fundacion/fundacion/1").with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/tutores-fundacion/fundacion/2147483647").with(user("a").roles("ADMIN")))
           .andExpect(status().isNotFound());
    }

    @Test
    void estudiante_puede_acceder_al_reenvio_personal_de_bitacora() throws Exception {
        mvc.perform(put("/api/bitacoras/2147483647/reenviar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"2026-07-20\",\"actividad\":\"Actividad corregida\",\"horas\":4}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isNotFound());
    }

    @Test
    void estudiante_no_configura_documentos_requeridos() throws Exception {
        mvc.perform(post("/api/documentos/requeridos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_configura_documentos_requeridos() throws Exception {
        mvc.perform(post("/api/documentos/requeridos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_usa_endpoint_revision_de_archivos_documentales() throws Exception {
        mvc.perform(get("/api/documentos/1/archivo")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_sube_ni_revisa_archivos_documentales() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "documento.pdf", "application/pdf", "contenido".getBytes());

        mvc.perform(multipart("/api/documentos/me/1/archivo")
                .file(archivo)
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());

        mvc.perform(get("/api/documentos/1/archivo")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_y_tutor_no_acceden_bandeja_revision_documental() throws Exception {
        mvc.perform(get("/api/documentos/revision")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
        mvc.perform(put("/api/documentos/1/revision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"aprobado\"}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());

        mvc.perform(get("/api/documentos/revision")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(put("/api/documentos/1/revision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"aprobado\"}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_accede_a_notas_academicas() throws Exception {
        mvc.perform(get("/api/notas-academicas")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/notas-academicas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void postulacion_rechaza_preferencias_minimas_y_repetidas() throws Exception {
        Estudiante estudiante = estudianteRepository.findAll().stream().findFirst().orElse(null);
        Assumptions.assumeTrue(estudiante != null, "No hay estudiantes para probar validacion de preferencias");

        mvc.perform(post("/api/postulaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "estudiante": { "id": %d },
                          "pref1": { "id": 1 }
                        }
                        """.formatted(estudiante.getId()))
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());

        mvc.perform(post("/api/postulaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "estudiante": { "id": %d },
                          "pref1": { "id": 1 },
                          "pref2": { "id": 1 },
                          "promedio": 10,
                          "score": 10
                        }
                        """.formatted(estudiante.getId()))
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void catalogos_rechazan_cupos_negativos() throws Exception {
        mvc.perform(post("/api/vacantes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Vacante cupos negativos",
                          "cupos": -1,
                          "horas": 240,
                          "descripcion": "Prueba",
                          "carrera": "Ingeniería en Software",
                          "modalidadAcademica": "Práctica I",
                          "area": "Sistemas",
                          "ciudad": "Quito",
                          "tipoEmpresa": "Privada",
                          "modalidadTrabajo": "Presencial"
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());

        mvc.perform(post("/api/proyectos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Proyecto cupos negativos",
                          "cuposDisponibles": -1
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void calendario_rechaza_tipo_invalido_y_fechas_desordenadas() throws Exception {
        mvc.perform(post("/api/configuracion/fechas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "periodoAcademico": "2099-1",
                          "tipo": "OTRO",
                          "convocatoriaInicio": "2099-01-10",
                          "fechaLimiteDocumentos": "2099-01-15",
                          "fechaInicioPostulacion": "2099-01-20",
                          "convocatoriaFin": "2099-01-30"
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());

        mvc.perform(post("/api/configuracion/fechas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "periodoAcademico": "2099-1",
                          "tipo": "PRACTICAS",
                          "convocatoriaInicio": "2099-01-10",
                          "fechaLimiteDocumentos": "2099-01-09",
                          "fechaInicioPostulacion": "2099-01-20",
                          "convocatoriaFin": "2099-01-30"
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void calendario_rechaza_parcial_invalido() throws Exception {
        mvc.perform(post("/api/configuracion/fechas-limite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "periodoAcademico": "2099-1",
                          "parcial": 4,
                          "fechaLimite": "2099-02-01"
                        }
                        """)
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void roles_no_estudiante_no_marcan_encuesta() throws Exception {
        mvc.perform(post("/api/evaluaciones/encuesta")
                .param("practicaId", "1")
                .param("parcial", "1")
                .with(user("a").roles("ADMIN")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/evaluaciones/encuesta")
                .param("practicaId", "1")
                .param("parcial", "1")
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/evaluaciones/encuesta")
                .param("practicaId", "1")
                .param("parcial", "1")
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_registra_notas() throws Exception {
        mvc.perform(post("/api/evaluaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    @Test
    void tutor_no_registra_nota_de_coordinador() throws Exception {
        Practica practica = practicaConEstudiante();

        mvc.perform(post("/api/evaluaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "practica": { "id": %d },
                          "parcial": 1,
                          "notaCoord": 9
                        }
                        """.formatted(practica.getId()))
                .with(user("t").roles("TUTOR")))
           .andExpect(status().isForbidden());
    }

    @Test
    void coordinador_no_registra_nota_de_tutor() throws Exception {
        Practica practica = practicaConEstudiante();

        mvc.perform(post("/api/evaluaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "practica": { "id": %d },
                          "parcial": 1,
                          "notaTutor": 9
                        }
                        """.formatted(practica.getId()))
                .with(user("c").roles("COORDINADOR")))
           .andExpect(status().isForbidden());
    }

    // ---------- NOTIFICACIONES (fase 12) ----------

    @Test
    void anonimo_no_accede_a_notificaciones() throws Exception {
        mvc.perform(get("/api/notificaciones/me")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/notificaciones/me/no-leidas/count")).andExpect(status().is4xxClientError());
    }

    @Test
    void todos_los_roles_ven_su_bandeja_de_notificaciones() throws Exception {
        mvc.perform(get("/api/notificaciones/me").with(user("a").roles("ADMIN")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/notificaciones/me").with(user("c").roles("COORDINADOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/notificaciones/me").with(user("t").roles("TUTOR")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/notificaciones/me").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/notificaciones/me/no-leidas/count").with(user("e").roles("ESTUDIANTE")))
           .andExpect(status().isOk());
    }

    @Test
    void comunicaciones_de_seguimiento_no_son_publicas_ni_accesibles_para_empresa() throws Exception {
        mvc.perform(get("/api/comentarios-seguimiento/me"))
           .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/comentarios-seguimiento/me").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/comentarios-seguimiento")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
    }

    @Test
    void estudiante_no_marca_leida_notificacion_ajena() throws Exception {
        Notificacion notificacion = notificacionRepository.findAll().stream()
                .filter(n -> n.getUsuarioDestino() != null)
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(notificacion != null,
                "No hay notificaciones para probar autorización horizontal");
        Usuario otro = usuarioRepository.findAll().stream()
                .filter(u -> !u.getId().equals(notificacion.getUsuarioDestino().getId()))
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(otro != null, "No hay usuario alterno para probar notificación ajena");

        mvc.perform(put("/api/notificaciones/" + notificacion.getId() + "/leida")
                .with(usuario(otro, "ESTUDIANTE")))
           .andExpect(status().isForbidden());
    }

    // ---------- EMPRESA (rol fuera del MVP: sin acceso) ----------

    @Test
    void empresa_no_accede_al_api() throws Exception {
        mvc.perform(get("/api/vacantes").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/practicas").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/practicas/1/acta").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/notificaciones/me").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/convenios").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/importaciones/estudiantes").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/asignaciones").with(user("x").roles("EMPRESA")))
           .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/me").with(user("x").roles("EMPRESA"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nuevo@unibe.edu.ec\"}"))
           .andExpect(status().isForbidden());
    }

    // Positivo: los 4 roles permitidos deben poder llegar al controlador
    // (400 por cuerpo vacio, no 403 por la matriz). Se manda un cuerpo
    // invalido a proposito para no escribir en la base de Supabase. Requiere
    // una entidad Usuario real (via el helper usuario(...)): el controlador
    // usa @AuthenticationPrincipal Usuario, que no resuelve con user().roles().
    @Test
    void los_cuatro_roles_permitidos_llegan_al_endpoint_de_actualizar_mi_correo() throws Exception {
        for (String rol : List.of("ADMIN", "COORDINADOR", "TUTOR", "ESTUDIANTE")) {
            Usuario titular = usuarioRepository.findDistinctByRoles_CodigoAndActivoTrueOrderByApellidoAscNombreAsc(rol)
                    .stream().findFirst().orElse(null);
            Assumptions.assumeTrue(titular != null, "No hay usuario activo con rol " + rol + " para probar el endpoint");
            mvc.perform(patch("/api/usuarios/me").with(usuario(titular, rol))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
               .andExpect(status().isBadRequest());
        }
    }

    @Test
    void usuario_con_primer_login_no_accede_api_hasta_cambiar_password() throws Exception {
        Usuario usuario = usuarioRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .filter(u -> Boolean.TRUE.equals(u.getPrimerLogin()))
                .filter(u -> u.getRoles() != null && !u.getRoles().isEmpty())
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(usuario != null, "No hay usuarios con primer_login=true para probar el bloqueo");
        String rol = usuario.getRoles().stream().findFirst().map(r -> r.getCodigo()).orElse(null);
        Assumptions.assumeTrue(rol != null, "Usuario de primer_login sin rol");
        String token = jwtTokenProvider.generateToken(usuario.getEmail(), rol);

        mvc.perform(get("/api/usuarios/me")
                .header("Authorization", "Bearer " + token))
           .andExpect(status().is(428));

        mvc.perform(post("/api/auth/cambiar-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer " + token))
           .andExpect(result -> assertNotEquals(428, result.getResponse().getStatus()));
    }

    private RequestPostProcessor usuario(Usuario usuario, String rol) {
        return authentication(new UsernamePasswordAuthenticationToken(
                usuario,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Practica practicaConEstudiante() {
        Practica practica = practicaRepository.findAll().stream()
                .filter(p -> p.getEstudiante() != null)
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(practica != null, "No hay prácticas para probar autorización horizontal");
        return practica;
    }

}
