package ec.edu.unibe.sistema_practicas.notificacion;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CorreoColaProcesador {

    private static final Logger log = LoggerFactory.getLogger(CorreoColaProcesador.class);

    private final CorreoColaRepository correoColaRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:${APP_MAIL_ENABLED:false}}")
    private boolean mailEnabled;

    @Value("${app.mail.from:${APP_MAIL_FROM:no-reply@unibe.edu.ec}}")
    private String from;

    @Value("${app.mail.from-name:${APP_MAIL_FROM_NAME:Sistema de Practicas y Vinculacion UNIBE}}")
    private String fromName;

    @Scheduled(fixedDelay = 60000)
    public void procesarCola() {
        if (!mailEnabled) {
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Correo habilitado, pero no hay JavaMailSender configurado. Revisa spring.mail.*.");
            return;
        }

        // Límite de 50 correos por minuto para no saturar el servidor ni ser bloqueados por anti-spam
        List<CorreoCola> pendientes = correoColaRepository.findPendientesYFallidosConLimites(3, org.springframework.data.domain.PageRequest.of(0, 50));
        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Procesando {} correos en cola...", pendientes.size());

        for (CorreoCola correo : pendientes) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(
                        message,
                        true,
                        StandardCharsets.UTF_8.name()
                );
                helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
                helper.setTo(correo.getDestinatario());
                helper.setSubject(correo.getAsunto());
                helper.setText(textoPlanoDesdeHtml(correo.getCuerpoHtml()), correo.getCuerpoHtml());
                
                mailSender.send(message);

                correo.setEstado("ENVIADO");
                correo.setUltimoError(null);
            } catch (Exception e) {
                log.warn("Fallo al enviar correo a {}: {}", correo.getDestinatario(), e.getMessage());
                correo.setEstado("FALLIDO");
                correo.setUltimoError(e.getMessage());
            } finally {
                correo.setIntentos(correo.getIntentos() + 1);
                correo.setFechaActualizacion(LocalDateTime.now());
                correoColaRepository.save(correo);
            }
            
            // Pausa de 500ms (medio segundo) para simular envío dosificado (Rate-Limiting)
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Genera un texto plano legible a partir del HTML del correo, como respaldo (fallback)
     * para clientes de correo que no renderizan HTML. Tener siempre una parte de texto plano
     * real (no un placeholder genérico) reduce el riesgo de que el correo sea marcado como
     * spam. El HTML que generamos internamente es predecible, así que basta un stripping
     * simple de etiquetas por regex, sin depender de una librería externa.
     */
    private String textoPlanoDesdeHtml(String cuerpoHtml) {
        if (cuerpoHtml == null || cuerpoHtml.isBlank()) {
            return "Este correo requiere un cliente compatible con HTML.";
        }
        String texto = cuerpoHtml
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n\n")
                .replaceAll("(?is)</tr>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&aacute;", "á")
                .replace("&eacute;", "é")
                .replace("&iacute;", "í")
                .replace("&oacute;", "ó")
                .replace("&uacute;", "ú")
                .replace("&middot;", "-");

        texto = texto.replaceAll("[ \\t]+", " ");
        texto = texto.replaceAll(" *\\n *", "\n");
        texto = texto.replaceAll("\\n{3,}", "\n\n");
        return texto.trim();
    }
}
