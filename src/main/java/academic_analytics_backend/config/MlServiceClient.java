package academic_analytics_backend.config;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MlServiceClient {
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\\\"(?:detail|message)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private final RestClient client;

    public MlServiceClient(@Value("${app.ml.base-url:}") String baseUrl,
                           @Value("${app.ml.api-key:}") String apiKey) {
        this.client = baseUrl == null || baseUrl.isBlank() ? null : RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("X-API-Key", apiKey == null ? "" : apiKey)
                .build();
    }

    public boolean isConfigured() { return client != null; }

    public Map<String, Object> train(List<MultipartFile> files) {
        requireConfigured();
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename() == null ? "dataset.csv" : file.getOriginalFilename();
                HttpHeaders partHeaders = new HttpHeaders();
                partHeaders.setContentDispositionFormData("files", filename);
                partHeaders.setContentType(MediaType.TEXT_PLAIN);
                ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                    @Override public String getFilename() { return filename; }
                };
                body.add("files", new HttpEntity<>(resource, partHeaders));
            }
            Map<String, Object> result = client.post().uri("/train")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            return result == null ? Map.of("modelTrained", false) : result;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read an uploaded CSV.");
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 413) {
                throw new ResponseStatusException(ex.getStatusCode(), trainingErrorMessage(ex));
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The ML service rejected the training request. Check its API key and deployment logs.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML training request failed. Check the CSV requirements and service logs.");
        }
    }

    private String trainingErrorMessage(HttpClientErrorException ex) {
        Matcher matcher = ERROR_MESSAGE.matcher(ex.getResponseBodyAsString());
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\n", " ").replace("\\r", " ");
        }
        return "The ML service rejected the CSV. Check the required columns and row values.";
    }

    public Map<String, Object> predict(double attendancePercent, double averageMarksPercent, String modelArtifact) {
        return predict("active", Map.of(
                "attendance_percent", attendancePercent,
                "average_marks_percent", averageMarksPercent), modelArtifact);
    }

    public Map<String, Object> predict(String modelKey, Map<String, Double> features, String modelArtifact) {
        requireConfigured();
        try {
            Map<String, Object> result = client.post().uri("/predict").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("modelKey", modelKey, "features", features,
                            "modelArtifact", modelArtifact))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (result == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned no prediction.");
            return result;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 400) {
                Matcher matcher = ERROR_MESSAGE.matcher(ex.getResponseBodyAsString());
                String message = matcher.find() ? matcher.group(1).replace("\\\"", "\"")
                        : "The input values do not meet the model requirements.";
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The prediction service rejected the request. Check the ML service configuration.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No trained model is available, or the ML service is unavailable.");
        }
    }

    private void requireConfigured() {
        if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ML_SERVICE_URL is not configured.");
    }
}
