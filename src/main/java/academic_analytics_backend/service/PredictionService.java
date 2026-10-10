package academic_analytics_backend.service;

import academic_analytics_backend.config.MlServiceClient;
import academic_analytics_backend.dto.StudentPredictionResponse;
import academic_analytics_backend.dto.ModelStatusResponse;
import academic_analytics_backend.entity.AttendanceStatus;
import academic_analytics_backend.entity.TrainedModelArtifact;
import academic_analytics_backend.repository.AttendanceRecordRepository;
import academic_analytics_backend.repository.MarkRecordRepository;
import academic_analytics_backend.repository.StudentRepository;
import academic_analytics_backend.repository.TrainedModelArtifactRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;

@Service
public class PredictionService {
    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final MarkRecordRepository marks;
    private final MlServiceClient ml;
    private final TrainedModelArtifactRepository modelRepository;

    public PredictionService(StudentRepository students, AttendanceRecordRepository attendance,
                             MarkRecordRepository marks, MlServiceClient ml,
                             TrainedModelArtifactRepository modelRepository) {
        this.students = students;
        this.attendance = attendance;
        this.marks = marks;
        this.ml = ml;
        this.modelRepository = modelRepository;
    }

    @Transactional(readOnly = true)
    public ModelStatusResponse status() {
        var model = modelRepository.findMetadataById(TrainedModelArtifact.ACTIVE_MODEL_ID).orElse(null);
        if (model == null) return new ModelStatusResponse(ml.isConfigured(), false, null, null);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("mae", model.getMae());
        metrics.put("rmse", model.getRmse());
        metrics.put("r2", model.getR2());
        metrics.put("datasetRows", model.getDatasetRows());
        metrics.put("trainRows", model.getTrainRows());
        metrics.put("testRows", model.getTestRows());
        return new ModelStatusResponse(ml.isConfigured(), true, metrics, model.getTrainedAt());
    }

    public Map<String, Object> train(org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> result = ml.train(file);
        Object artifactValue = result.get("modelArtifact");
        Object metricsValue = result.get("metrics");
        if (!(artifactValue instanceof String artifactBase64) || !(metricsValue instanceof Map<?, ?> metrics)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned an incomplete training result.");
        }
        byte[] artifactBytes;
        try {
            artifactBytes = Base64.getDecoder().decode(artifactBase64);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned an invalid model artifact.");
        }
        if (artifactBytes.length == 0 || artifactBytes.length > 25 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Trained model artifact exceeds the storage limit.");
        }
        TrainedModelArtifact model = new TrainedModelArtifact();
        model.setId(TrainedModelArtifact.ACTIVE_MODEL_ID);
        model.setModelBytes(artifactBytes);
        model.setAlgorithm(String.valueOf(result.getOrDefault("algorithm", "RandomForestRegressor")));
        model.setMae(number(metrics, "mae"));
        model.setRmse(number(metrics, "rmse"));
        model.setR2(number(metrics, "r2"));
        model.setDatasetRows(integer(metrics, "datasetRows"));
        model.setTrainRows(integer(metrics, "trainRows"));
        model.setTestRows(integer(metrics, "testRows"));
        model.setTrainedAt(String.valueOf(result.getOrDefault("trainedAt", "")));
        modelRepository.save(model);
        Map<String, Object> publicResult = new HashMap<>(result);
        publicResult.remove("modelArtifact");
        return publicResult;
    }

    @Transactional(readOnly = true)
    public StudentPredictionResponse predict(Long studentId) {
        var storedModel = modelRepository.findById(TrainedModelArtifact.ACTIVE_MODEL_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No trained model is available. An administrator must upload a labeled dataset."));
        var student = students.findById(studentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        var attendanceRows = attendance.findByStudent_IdOrderByClassDateDesc(studentId);
        var markRows = marks.findByStudent_IdOrderByAssessedAtDesc(studentId);
        if (attendanceRows.isEmpty() || markRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Record both attendance and marks before requesting a prediction.");
        }
        long present = attendanceRows.stream().filter(row -> row.getStatus() == AttendanceStatus.PRESENT).count();
        double attendancePercent = BigDecimal.valueOf(present * 100.0 / attendanceRows.size())
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        BigDecimal score = markRows.stream().map(row -> row.getScore()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maxScore = markRows.stream().map(row -> row.getMaxScore()).reduce(BigDecimal.ZERO, BigDecimal::add);
        double marksPercent = score.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 1, RoundingMode.HALF_UP).doubleValue();
        String modelArtifact = Base64.getEncoder().encodeToString(storedModel.getModelBytes());
        Map<String, Object> result = ml.predict(attendancePercent, marksPercent, modelArtifact);
        Object predictionsObject = result.get("predictions");
        if (!(predictionsObject instanceof List<?> predictions) || predictions.isEmpty()
                || !(predictions.getFirst() instanceof Number predicted)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned an invalid prediction.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = result.get("modelMetrics") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw : Map.of();
        return new StudentPredictionResponse(student.getId(), student.getFullName(), attendancePercent,
                marksPercent, predicted.doubleValue(), String.valueOf(result.getOrDefault("target", "final_score")),
                metrics, String.valueOf(result.getOrDefault("trainedAt", "")),
                List.of("attendance_percent", "average_marks_percent"));
    }

    private static double number(Map<?, ?> metrics, String key) {
        Object value = metrics.get(key);
        if (value instanceof Number number) return number.doubleValue();
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service omitted a required metric.");
    }

    private static int integer(Map<?, ?> metrics, String key) {
        Object value = metrics.get(key);
        if (value instanceof Number number) return number.intValue();
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service omitted training split details.");
    }
}
