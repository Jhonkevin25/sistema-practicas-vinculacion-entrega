package ec.edu.unibe.sistema_practicas.documento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SupabaseStorageComponent {

    private static final Pattern SIGNED_URL_PATTERN =
            Pattern.compile("\"signedURL\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.storage.supabase-url:${SUPABASE_URL:}}")
    private String supabaseUrl;

    @Value("${app.storage.service-key:${SUPABASE_SERVICE_ROLE_KEY:}}")
    private String serviceKey;

    @Value("${app.storage.bucket:${SUPABASE_STORAGE_BUCKET:documentos}}")
    private String bucket;

    @Value("${app.storage.signed-url-seconds:${SUPABASE_STORAGE_SIGNED_URL_SECONDS:300}}")
    private long signedUrlSeconds;

    public void subir(String ruta, MultipartFile archivo) throws IOException, InterruptedException {
        asegurarConfiguracion();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("%s/storage/v1/object/%s/%s".formatted(baseUrl(), encode(bucket), encodePath(ruta))))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey)
                .header("Content-Type", archivo.getContentType())
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(archivo.getBytes()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Supabase Storage rechazo la subida del documento. status={} body={}",
                    response.statusCode(), cuerpoSeguro(response.body()));
            throw new IllegalStateException("No se pudo guardar el archivo en Storage.");
        }
    }

    public String crearUrlFirmada(String ruta) throws IOException, InterruptedException {
        asegurarConfiguracion();

        String body = "{\"expiresIn\":%d}".formatted(signedUrlSeconds);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("%s/storage/v1/object/sign/%s/%s".formatted(baseUrl(), encode(bucket), encodePath(ruta))))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Supabase Storage rechazo la URL firmada del documento. status={} body={}",
                    response.statusCode(), cuerpoSeguro(response.body()));
            throw new IllegalStateException("No se pudo generar el enlace temporal del archivo.");
        }

        return normalizarUrlFirmada(extraerUrlFirmada(response.body()));
    }

    private void asegurarConfiguracion() {
        if (isBlank(supabaseUrl) || isBlank(serviceKey) || isBlank(bucket)) {
            throw new IllegalStateException("Storage de documentos no configurado.");
        }
    }

    private String extraerUrlFirmada(String json) {
        Matcher matcher = SIGNED_URL_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Storage no devolvio un enlace temporal valido.");
        }
        return matcher.group(1)
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\\"", "\"");
    }

    private String normalizarUrlFirmada(String signedUrl) {
        if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
            return signedUrl;
        }
        if (signedUrl.startsWith("/object/")) {
            return baseUrl() + "/storage/v1" + signedUrl;
        }
        if (signedUrl.startsWith("/")) {
            return baseUrl() + signedUrl;
        }
        return baseUrl() + "/" + signedUrl;
    }

    private String baseUrl() {
        String url = supabaseUrl.trim().replaceAll("/+$", "");
        url = url.replaceFirst("(?i)/rest/v1$", "");
        url = url.replaceFirst("(?i)/storage/v1$", "");
        return url;
    }

    private String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(this::encode)
                .collect(Collectors.joining("/"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String cuerpoSeguro(String body) {
        if (body == null || body.isBlank()) {
            return "(vacio)";
        }
        String limpio = body.replaceAll("[\\r\\n\\t]+", " ").trim();
        return limpio.length() <= 300 ? limpio : limpio.substring(0, 300);
    }
}
