package academic_analytics_backend.controller;

import academic_analytics_backend.dto.StudentPredictionResponse;
import academic_analytics_backend.service.PredictionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/predictions")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
public class PredictionController {
    private final PredictionService predictions;
    public PredictionController(PredictionService predictions) { this.predictions = predictions; }
    @GetMapping("/status") public academic_analytics_backend.dto.ModelStatusResponse status() { return predictions.status(); }
    @PostMapping(value = "/train", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> train(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty CSV file up to 10 MB.");
        }
        return predictions.train(file);
    }
    @PostMapping("/{studentId}")
    public StudentPredictionResponse predict(@PathVariable Long studentId) { return predictions.predict(studentId); }
}
