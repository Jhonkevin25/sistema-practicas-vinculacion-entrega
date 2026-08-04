package ec.edu.unibe.sistema_practicas.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Roles del MVP. EMPRESA existe en la BD pero no tiene acceso a la API.
    private static final String ADMIN = "ADMIN";
    private static final String COORDINADOR = "COORDINADOR";
    private static final String TUTOR = "TUTOR";
    private static final String ESTUDIANTE = "ESTUDIANTE";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Health check para monitoreo y plataforma de despliegue; el
                // resto de Actuator queda cerrado.
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/actuator/**").denyAll()

                .requestMatchers(HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/recuperar",
                        "/api/auth/restablecer").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/cambiar-password")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers("/api/auth/**").denyAll()

                // Registro propio del usuario autenticado (tutor/estudiante necesitan su id)
                .requestMatchers(HttpMethod.GET, "/api/usuarios/me", "/api/estudiantes/me")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                // Perfil propio: el dueño de la cuenta solo puede editar su correo
                .requestMatchers(HttpMethod.PATCH, "/api/usuarios/me")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)

                // Coordinacion recibe solo el directorio minimo de tutores;
                // el listado completo y la administracion quedan en ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/usuarios/tutores")
                    .hasAnyRole(ADMIN, COORDINADOR)
                // Candidatos para el alta de expediente (rol ESTUDIANTE, activos
                // y sin expediente): solo ADMIN, que es quien crea estudiantes.
                .requestMatchers(HttpMethod.GET, "/api/usuarios/candidatos-estudiante")
                    .hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/usuarios/**")
                    .hasRole(ADMIN)
                .requestMatchers("/api/usuarios/**").hasRole(ADMIN)

                // Estudiantes: lectura para tutores; el alta y la baja del
                // expediente son exclusivas de ADMIN; COORDINADOR solo edita
                // (el controlador limita sus campos a semestre y periodo).
                .requestMatchers(HttpMethod.GET, "/api/estudiantes/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR)
                .requestMatchers(HttpMethod.POST, "/api/estudiantes/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/estudiantes/**").hasRole(ADMIN)
                .requestMatchers("/api/estudiantes/**").hasAnyRole(ADMIN, COORDINADOR)

                // Convenios: ADMIN gestiona; COORDINADOR consulta.
                .requestMatchers(HttpMethod.GET, "/api/convenios", "/api/convenios/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers("/api/convenios", "/api/convenios/**")
                    .hasRole(ADMIN)

                // Oferta operativa de cupos: gestion consulta; solo ADMIN
                // configura la capacidad compartida por empresa y periodo.
                .requestMatchers(HttpMethod.GET,
                        "/api/ofertas-cupos-empresa", "/api/ofertas-cupos-empresa/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(
                        "/api/ofertas-cupos-empresa", "/api/ofertas-cupos-empresa/**")
                    .hasRole(ADMIN)

                // Tutores externos: gestión consulta los vínculos compatibles
                // con empresa/carrera; solo ADMIN configura esas relaciones.
                .requestMatchers(HttpMethod.GET,
                        "/api/tutores-empresa", "/api/tutores-empresa/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(
                        "/api/tutores-empresa", "/api/tutores-empresa/**")
                    .hasRole(ADMIN)

                // Espejo para Vinculación: tutores externos por fundación/carrera.
                .requestMatchers(HttpMethod.GET,
                        "/api/tutores-fundacion", "/api/tutores-fundacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(
                        "/api/tutores-fundacion", "/api/tutores-fundacion/**")
                    .hasRole(ADMIN)

                // La capacidad de fundaciones sigue la misma regla: gestion
                // consulta y solo ADMIN configura cupos por periodo/carrera.
                .requestMatchers(HttpMethod.GET,
                        "/api/ofertas-cupos-fundacion", "/api/ofertas-cupos-fundacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(
                        "/api/ofertas-cupos-fundacion", "/api/ofertas-cupos-fundacion/**")
                    .hasRole(ADMIN)

                // Importaciones institucionales: archivos y datos sensibles, solo ADMIN.
                .requestMatchers("/api/importaciones/**")
                    .hasRole(ADMIN)

                // Panel de correos: solo ADMIN consulta, reintenta o elimina.
                .requestMatchers(HttpMethod.GET, "/api/correos/paginado")
                    .hasRole(ADMIN)
                .requestMatchers(HttpMethod.POST, "/api/correos/{id}/reintentar")
                    .hasRole(ADMIN)
                .requestMatchers(HttpMethod.DELETE, "/api/correos/{id}")
                    .hasRole(ADMIN)
                .requestMatchers("/api/correos/**").denyAll()

                // Catalogos: lectura para todos los roles MVP,
                // escritura solo ADMIN/COORDINADOR
                .requestMatchers(HttpMethod.GET,
                        "/api/empresas/**", "/api/fundaciones/**", "/api/proyectos/**",
                        "/api/vacantes/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(
                        "/api/empresas/**", "/api/fundaciones/**", "/api/proyectos/**",
                        "/api/vacantes/**")
                    .hasAnyRole(ADMIN, COORDINADOR)

                // Catalogo de carreras: consulta para gestion academica;
                // solo ADMIN lo administra.
                .requestMatchers(HttpMethod.GET, "/api/carreras/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers("/api/carreras/**").hasRole(ADMIN)

                // Configuracion academica: todos consultan fechas, solo ADMIN
                // puede crear/editar/eliminar.
                .requestMatchers(HttpMethod.GET, "/api/configuracion/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers("/api/configuracion/**").hasRole(ADMIN)

                // Evaluaciones: lectura todos; encuesta la marca el estudiante;
                // registrar notas solo tutor/coordinador/admin
                .requestMatchers(HttpMethod.GET, "/api/evaluaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/evaluaciones/encuesta")
                    .hasRole(ESTUDIANTE)
                .requestMatchers("/api/evaluaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR)

                // Favoritos de vacantes: personales del estudiante (el controlador
                // resuelve el expediente desde el JWT)
                .requestMatchers("/api/favoritos/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)

                // Auditoria completa: contiene trazas institucionales y queda
                // exclusivamente bajo supervision de ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/auditoria/**")
                    .hasRole(ADMIN)
                .requestMatchers("/api/auditoria/**").denyAll()

                // Reportes operativos y exportacion CSV: solo gestion academica.
                // El controlador aplica ademas el alcance por carrera.
                .requestMatchers(HttpMethod.GET, "/api/reportes/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers("/api/reportes/**").denyAll()

                // Notificaciones internas: bandeja personal de cada usuario
                // (endpoints /me o validados por destinatario en el controlador)
                .requestMatchers("/api/notificaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)

                // Documentos del expediente: personales del estudiante (el
                // controlador resuelve el expediente desde el JWT)
                .requestMatchers(HttpMethod.GET, "/api/documentos/requeridos/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/documentos/requeridos/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.PUT, "/api/documentos/requeridos/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.DELETE, "/api/documentos/requeridos/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/documentos/revision")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.PUT, "/api/documentos/*/revision")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.POST, "/api/documentos/me/*/archivo")
                    .hasRole(ESTUDIANTE)
                .requestMatchers(HttpMethod.GET, "/api/documentos/me/*/archivo")
                    .hasRole(ESTUDIANTE)
                .requestMatchers(HttpMethod.GET, "/api/documentos/*/archivo")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/documentos/me/**")
                    .hasRole(ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/documentos/me/**")
                    .hasRole(ESTUDIANTE)
                .requestMatchers("/api/documentos/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)

                // Notas academicas oficiales: el estudiante solo consulta las
                // suyas; el registro llega por importacion institucional o por
                // gestion academica, y la verificacion es de coordinacion.
                .requestMatchers(HttpMethod.GET, "/api/notas-academicas/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)
                .requestMatchers("/api/notas-academicas/**")
                    .hasAnyRole(ADMIN, COORDINADOR)

                // Alcance del coordinador: el coordinador solo consulta el
                // propio; ADMIN consulta y configura el de otro usuario.
                .requestMatchers(HttpMethod.GET, "/api/coordinadores/alcance/me")
                    .hasRole(COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/coordinadores/*/alcance")
                    .hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/coordinadores/*/alcance")
                    .hasRole(ADMIN)
                .requestMatchers("/api/coordinadores/**").denyAll()

                // Practicas y vinculacion: el tutor registra notas y revisa
                // bitacoras en sus endpoints dedicados; el expediente solo lo
                // modifica gestion academica.
                .requestMatchers(HttpMethod.GET, "/api/practicas/seguimiento", "/api/practicas/seguimiento/paginado",
                        "/api/vinculacion/seguimiento", "/api/vinculacion/seguimiento/paginado")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/practicas/*/linea-tiempo", "/api/practicas/me/linea-tiempo",
                        "/api/vinculacion/*/linea-tiempo", "/api/vinculacion/me/linea-tiempo")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.GET, "/api/practicas/*/acta", "/api/vinculacion/*/acta")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/practicas/*/cerrar", "/api/vinculacion/*/cerrar")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.POST, "/api/practicas/*/finalizar-excepcional",
                        "/api/vinculacion/*/finalizar-excepcional")
                    .hasAnyRole(ADMIN, COORDINADOR)
                // Fases 42 y 47: liberar el cupo de un expediente retirado (una
                // sola vez, con justificacion) es una accion de gestion academica.
                .requestMatchers(HttpMethod.POST, "/api/vinculacion/*/liberar-cupo",
                        "/api/practicas/*/liberar-cupo")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/vinculacion/postulaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/vinculacion/postulaciones")
                    .hasRole(ESTUDIANTE)
                .requestMatchers(HttpMethod.POST,
                        "/api/vinculacion/postulaciones/*/aprobar",
                        "/api/vinculacion/postulaciones/*/rechazar")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.GET, "/api/practicas/**", "/api/vinculacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.PUT, "/api/practicas/**", "/api/vinculacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers("/api/practicas/**", "/api/vinculacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR)

                // Postulaciones: el estudiante postula; procesar/eliminar es gestion academica
                .requestMatchers(HttpMethod.GET, "/api/postulaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/postulaciones/procesar-meritocracia")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.POST, "/api/postulaciones/*/consolidar-practica")
                    .hasAnyRole(ADMIN, COORDINADOR)
                .requestMatchers(HttpMethod.POST, "/api/postulaciones/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)
                .requestMatchers("/api/postulaciones/**").hasAnyRole(ADMIN, COORDINADOR)

                // Bitacoras: registro por estudiante y revision por tutor.
                // Asistencias: registro por tutor o gestion academica; lectura acotada en controller.
                .requestMatchers(HttpMethod.GET, "/api/bitacoras/**", "/api/asistencias/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/bitacoras/**")
                    .hasAnyRole(ADMIN, COORDINADOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.PUT, "/api/bitacoras/*/reenviar")
                    .hasRole(ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/asistencias/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR)
                .requestMatchers(HttpMethod.PUT, "/api/bitacoras/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR)
                .requestMatchers("/api/bitacoras/**", "/api/asistencias/**")
                    .hasAnyRole(ADMIN, COORDINADOR)

                // Notas de coordinacion: constancia libre del coordinador, visible
                // para tutor/estudiante pero solo ADMIN/COORDINADOR pueden crearla.
                .requestMatchers(HttpMethod.GET, "/api/notas-coordinacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST, "/api/notas-coordinacion/**")
                    .hasAnyRole(ADMIN, COORDINADOR)

                // Comunicaciones del expediente: los cuatro roles pueden leer
                // y escribir; el controlador aplica dueño, tutor asignado,
                // proceso, carrera y audiencias permitidas.
                .requestMatchers(HttpMethod.GET,
                        "/api/comentarios-seguimiento", "/api/comentarios-seguimiento/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)
                .requestMatchers(HttpMethod.POST,
                        "/api/comentarios-seguimiento", "/api/comentarios-seguimiento/**")
                    .hasAnyRole(ADMIN, COORDINADOR, TUTOR, ESTUDIANTE)

                // Alerta de atraso: disparo manual solo para ADMIN (herramienta
                // operativa; el cron diario no pasa por Security).
                .requestMatchers(HttpMethod.POST, "/api/seguimiento/alertas-atraso/ejecutar")
                    .hasRole(ADMIN)

                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "ngrok-skip-browser-warning"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
