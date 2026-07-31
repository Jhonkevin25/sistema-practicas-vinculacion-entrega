package ec.edu.unibe.sistema_practicas.notificacion;

/**
 * Plantilla de marca compartida para todos los correos del sistema (bienvenida,
 * recuperación de contraseña, notificaciones de bitácora/documentos, etc.).
 * <p>
 * Envuelve el fragmento HTML propio de cada correo (sin tocar su redacción) con un
 * encabezado azul marino institucional + logo UNIBE, una franja dorada de acento y un
 * pie de página. Usa layout basado en tablas y estilos inline exclusivamente, ya que
 * clientes de correo como Outlook o Gmail no soportan CSS moderno (flexbox/grid,
 * hojas de estilo externas) de forma confiable.
 * <p>
 * No es un bean de Spring: es una utilidad estática pura (solo depende de los
 * parámetros recibidos), por lo que no aplica la regla de "sin capa de servicio nueva".
 */
public final class EmailPlantillaComponent {

    private static final String AZUL_MARINO = "#00224d";
    private static final String DORADO = "#ffcc00";
    private static final String DEFAULT_BASE_URL = "http://localhost:4200";

    private EmailPlantillaComponent() {
    }

    /**
     * Envuelve el contenido HTML específico de un correo con la plantilla de marca.
     *
     * @param contenidoHtml fragmento HTML ya armado por el componente que envía el correo
     *                       (párrafos, enlaces, etc.), tal cual se debe mostrar en el cuerpo.
     * @param baseUrl        URL base del frontend (dev o producción), usada para resolver el
     *                       logo público en {@code ${baseUrl}/image/logo_unibe.png}.
     * @return el documento HTML completo, listo para usarse como cuerpo del correo.
     */
    public static String envolver(String contenidoHtml, String baseUrl) {
        String base = normalizarBaseUrl(baseUrl);
        String logoUrl = base + "/image/logo_unibe.png";
        String cuerpo = contenidoHtml == null ? "" : contenidoHtml;

        return """
                <!doctype html>
                <html lang="es">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Sistema de Prácticas y Vinculación UNIBE</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f3f4f6; font-family: Arial, Helvetica, sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f3f4f6; padding:24px 0; margin:0;">
                <tr>
                <td align="center" style="padding:0;">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="width:100%%; max-width:560px; background-color:#ffffff; border-radius:8px; overflow:hidden; border:1px solid #e5e7eb;">
                <tr>
                <td align="center" style="background-color:%s; padding:24px;">
                <img src="%s" alt="UNIBE" width="160" style="display:block; max-width:160px; height:auto; border:0; outline:none;">
                </td>
                </tr>
                <tr>
                <td style="background-color:%s; height:6px; line-height:6px; font-size:0;">&nbsp;</td>
                </tr>
                <tr>
                <td style="padding:28px 32px; color:#1f2937; font-family: Arial, Helvetica, sans-serif; line-height:1.5; font-size:14px;">
                %s
                </td>
                </tr>
                <tr>
                <td style="background-color:#f9fafb; padding:16px 32px; text-align:center; border-top:1px solid #e5e7eb;">
                <p style="margin:0; font-size:12px; color:#6b7280; font-family: Arial, Helvetica, sans-serif;">Sistema de Pr&aacute;cticas y Vinculaci&oacute;n &middot; Universidad Internacional del Ecuador (UNIBE)</p>
                </td>
                </tr>
                </table>
                </td>
                </tr>
                </table>
                </body>
                </html>
                """.formatted(AZUL_MARINO, logoUrl, DORADO, cuerpo);
    }

    private static String normalizarBaseUrl(String baseUrl) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
