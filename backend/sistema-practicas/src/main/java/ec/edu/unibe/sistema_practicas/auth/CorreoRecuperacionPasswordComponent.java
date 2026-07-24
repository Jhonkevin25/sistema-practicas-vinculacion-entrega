package ec.edu.unibe.sistema_practicas.auth;

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

@Component
@RequiredArgsConstructor
public class CorreoRecuperacionPasswordComponent {

    private static final Logger log = LoggerFactory.getLogger(CorreoRecuperacionPasswordComponent.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:${APP_MAIL_ENABLED:false}}")
    private boolean mailEnabled;

    @Value("${app.mail.from:${APP_MAIL_FROM:no-reply@unibe.edu.ec}}")
    private String from;

    @Value("${app.mail.from-name:${APP_MAIL_FROM_NAME:Sistema de Practicas y Vinculacion UNIBE}}")
    private String fromName;

    @Value("${app.mail.base-url:${APP_MAIL_BASE_URL:http://localhost:4200}}")
    private String baseUrl;

    @Async
    public void enviar(Usuario usuario, String token) {
        if (!mailEnabled) {
            log.debug("Correo desactivado; no se envia recuperacion de contraseña.");
            return;
        }
        if (usuario == null || !StringUtils.hasText(usuario.getEmail()) || !StringUtils.hasText(token)) {
            log.warn("No se envia recuperacion de contraseña: usuario/email/token incompleto.");
            return;
        }
        try {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                log.warn("Correo habilitado, pero no hay JavaMailSender configurado. Revisa spring.mail.*.");
                return;
            }

            String link = linkRestablecimiento(token);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(usuario.getEmail());
            helper.setSubject("UNIBE - Restablecer contraseña");
            helper.setText(textoPlano(usuario, link), html(usuario, link));
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de recuperación al usuario {}: {}",
                    usuario.getId(), e.getMessage());
            log.debug("Detalle del fallo SMTP", e);
        }
    }

    private String textoPlano(Usuario usuario, String link) {
        return """
                Hola %s,

                Recibimos una solicitud para restablecer tu contraseña del Sistema de Prácticas y Vinculación UNIBE.

                Usa este enlace durante los próximos minutos:
                %s

                Si no solicitaste este cambio, puedes ignorar este mensaje.
                """.formatted(nombreDestino(usuario), link);
    }

    private String html(Usuario usuario, String link) {
        String nombre = escapeHtml(nombreDestino(usuario));
        String enlace = escapeHtml(link);
        return """
                <!doctype html>
                <html lang="es">
                <body style="font-family: Arial, sans-serif; color: #1f2937; line-height: 1.5;">
                  <p>Hola %s,</p>
                  <h2 style="font-size: 18px; margin: 0 0 12px;">Restablecer contraseña</h2>
                  <p>Recibimos una solicitud para restablecer tu contraseña del Sistema de Prácticas y Vinculación UNIBE.</p>
                  <p><a href="%s" style="color: #0f766e;">Crear nueva contraseña</a></p>
                  <p style="font-size: 12px; color: #6b7280;">Si no solicitaste este cambio, puedes ignorar este mensaje.</p>
                </body>
                </html>
                """.formatted(nombre, enlace);
    }

    private String linkRestablecimiento(String token) {
        String base = StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost:4200";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/restablecer-password?token=" + token;
    }

    private String nombreDestino(Usuario usuario) {
        String nombre = "%s %s".formatted(
                usuario.getNombre() == null ? "" : usuario.getNombre(),
                usuario.getApellido() == null ? "" : usuario.getApellido()
        ).trim();
        return StringUtils.hasText(nombre) ? nombre : "usuario";
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
