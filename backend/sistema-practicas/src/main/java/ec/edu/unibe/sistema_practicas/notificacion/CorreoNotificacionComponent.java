package ec.edu.unibe.sistema_practicas.notificacion;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CorreoNotificacionComponent {

    private static final Logger log = LoggerFactory.getLogger(CorreoNotificacionComponent.class);

    private static final Set<String> TIPOS_CON_CORREO = Set.of(
            "documento_aprobado",
            "documento_rechazado",
            "postulacion_resuelta",
            "asignacion_practica",
            "asignacion_vinculacion",
            "bitacora_rechazada",
            "bitacora_requiere_correccion",
            "nota_registrada",
            "nota_coordinacion",
            "expediente_atrasado",
            "expediente_por_cerrar",
            "expediente_cerrado",
            "finalizacion_excepcional"
    );

    private final CorreoColaRepository correoColaRepository;

    @Value("${app.mail.enabled:${APP_MAIL_ENABLED:false}}")
    private boolean mailEnabled;

    @Value("${app.mail.from:${APP_MAIL_FROM:no-reply@unibe.edu.ec}}")
    private String from;

    @Value("${app.mail.from-name:${APP_MAIL_FROM_NAME:Sistema de Practicas y Vinculacion UNIBE}}")
    private String fromName;

    @Value("${app.mail.base-url:${APP_MAIL_BASE_URL:http://localhost:4200}}")
    private String baseUrl;

    @Async
    public void enviarSiAplica(Notificacion notificacion) {
        if (notificacion == null || !TIPOS_CON_CORREO.contains(notificacion.getTipo())) {
            return;
        }
        if (!mailEnabled) {
            log.debug("Correo desactivado; no se envia notificación tipo '{}'.", notificacion.getTipo());
            return;
        }

        Usuario destino = notificacion.getUsuarioDestino();
        if (destino == null || !StringUtils.hasText(destino.getEmail())) {
            log.warn("No se envia correo de notificación {}: destinatario sin email.", notificacion.getId());
            return;
        }

        try {
            CorreoCola cola = new CorreoCola();
            cola.setDestinatario(destino.getEmail());
            cola.setAsunto(asunto(notificacion));
            cola.setCuerpoHtml(html(notificacion, destino));
            cola.setFechaCreacion(java.time.LocalDateTime.now());
            cola.setFechaActualizacion(java.time.LocalDateTime.now());
            correoColaRepository.save(cola);
        } catch (Exception e) {
            log.warn("No se pudo encolar correo de notificación {} al usuario {}: {}",
                    notificacion.getId(), destino.getId(), e.getMessage());
        }
    }

    @Async
    public void enviarBienvenida(Usuario destino, String claveTemporal) {
        if (!mailEnabled) {
            log.debug("Correo desactivado; no se envía bienvenida institucional.");
            return;
        }
        if (destino == null || !StringUtils.hasText(destino.getEmail())
                || !StringUtils.hasText(claveTemporal)) {
            log.warn("No se envía bienvenida institucional: destinatario o clave temporal incompletos.");
            return;
        }

        try {
            String login = linkAbsoluto("/login");
            String nombre = nombreDestino(destino);
            
            CorreoCola cola = new CorreoCola();
            cola.setDestinatario(destino.getEmail());
            cola.setAsunto("UNIBE - Bienvenida al Sistema de Prácticas y Vinculación");
            cola.setCuerpoHtml(htmlBienvenida(nombre, destino.getEmail(), claveTemporal, login));
            cola.setFechaCreacion(java.time.LocalDateTime.now());
            cola.setFechaActualizacion(java.time.LocalDateTime.now());
            correoColaRepository.save(cola);
        } catch (Exception e) {
            log.warn("No se pudo encolar el correo de bienvenida al usuario {}: {}",
                    destino.getId(), e.getMessage());
        }
    }

    private String asunto(Notificacion notificacion) {
        String titulo = StringUtils.hasText(notificacion.getTitulo())
                ? notificacion.getTitulo()
                : "Notificación del sistema";
        return "UNIBE - " + titulo.replace('\n', ' ').replace('\r', ' ');
    }

    private String htmlBienvenida(String nombre, String email, String claveTemporal, String login) {
        String contenido = """
                <p>Hola %s,</p>
                <h2 style="font-size: 18px; margin: 0 0 12px;">Bienvenido al sistema UNIBE</h2>
                <p>Tu cuenta institucional fue creada para gestionar tus procesos de prácticas y vinculación.</p>
                <p><strong>Usuario:</strong> %s<br><strong>Clave temporal:</strong> %s</p>
                <p><a href="%s" style="color: #0f766e;">Ingresar al sistema</a></p>
                <p>El sistema solicitará cambiar la clave durante el primer acceso.</p>
                <p style="font-size: 12px; color: #6b7280;">No compartas tu clave temporal.</p>
                """.formatted(
                escapeHtml(nombre),
                escapeHtml(email),
                escapeHtml(claveTemporal),
                escapeHtml(login)
        );
        return EmailPlantillaComponent.envolver(contenido, baseUrl);
    }

    private String html(Notificacion notificacion, Usuario destino) {
        String nombre = escapeHtml(nombreDestino(destino));
        String titulo = escapeHtml(StringUtils.hasText(notificacion.getTitulo())
                ? notificacion.getTitulo()
                : "Notificación del sistema");
        String mensaje = escapeHtml(mensaje(notificacion)).replace("\n", "<br>");
        String link = escapeHtml(linkAbsoluto(notificacion.getLink()));
        String contenido = """
                <p>Hola %s,</p>
                <h2 style="font-size: 18px; margin: 0 0 12px;">%s</h2>
                <p>%s</p>
                <p>
                  <a href="%s" style="color: #0f766e;">Revisar en el sistema</a>
                </p>
                <p style="font-size: 12px; color: #6b7280;">
                  Este es un mensaje automático del Sistema de Prácticas y Vinculación UNIBE.
                </p>
                """.formatted(nombre, titulo, mensaje, link);
        return EmailPlantillaComponent.envolver(contenido, baseUrl);
    }

    private String mensaje(Notificacion notificacion) {
        return StringUtils.hasText(notificacion.getMensaje())
                ? notificacion.getMensaje()
                : "Tienes una actualización pendiente en el sistema.";
    }

    private String nombreDestino(Usuario destino) {
        String nombre = "%s %s".formatted(
                destino.getNombre() == null ? "" : destino.getNombre(),
                destino.getApellido() == null ? "" : destino.getApellido()
        ).trim();
        return StringUtils.hasText(nombre) ? nombre : "estudiante";
    }

    private String linkAbsoluto(String link) {
        if (StringUtils.hasText(link) && (link.startsWith("http://") || link.startsWith("https://"))) {
            return link;
        }
        String base = StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:4200";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!StringUtils.hasText(link)) {
            return base;
        }
        return base + (link.startsWith("/") ? link : "/" + link);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
