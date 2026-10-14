package academic_analytics_backend.controller;

import academic_analytics_backend.dto.StudentPredictionResponse;
import academic_analytics_backend.service.PredictionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/predictions")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
public class PredictionController {
    private final PredictionService predictions;
    public PredictionController(PredictionService predictions) { this.predictions = predictions; }
    @GetMapping("/status") public academic_analytics_backend.dto.ModelStatusResponse status() { return predictions.status(); }
    @GetMapping("/models") public Map<String, Object> models() { return predictions.catalog(); }
    @PostMapping(value = "/train", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> train(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "file", required = false) MultipartFile legacyFile) {
        List<MultipartFile> uploads = new ArrayList<>();
        if (files != null) uploads.addAll(files);
        if (legacyFile != null && !legacyFile.isEmpty()) uploads.add(legacyFile);
        if (uploads.isEmpty() || uploads.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 1 and 5 CSV files.");
        }
        for (MultipartFile file : uploads) {
            if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each CSV must be non-empty and no larger than 10 MB.");
            }
        }
        return predictions.train(uploads);
    }
    @PostMapping("/estimate/{modelKey}")
    public Map<String, Object> estimate(@PathVariable String modelKey, @RequestBody Map<String, Double> features) {
        return predictions.estimate(modelKey, features);
    }
    @PostMapping("/{studentId}")
    public StudentPredictionResponse predict(@PathVariable Long studentId) { return predictions.predict(studentId); }
}
