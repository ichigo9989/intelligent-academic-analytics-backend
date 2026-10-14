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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

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
        if (model == null) return new ModelStatusResponse(ml.isConfigured(), false, null, null, null, null);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("mae", model.getMae());
        metrics.put("rmse", model.getRmse());
        metrics.put("r2", model.getR2());
        if (model.getCvMae() != null) metrics.put("cvMae", model.getCvMae());
        metrics.put("datasetRows", model.getDatasetRows());
        metrics.put("trainRows", model.getTrainRows());
        metrics.put("testRows", model.getTestRows());
        return new ModelStatusResponse(ml.isConfigured(), true, model.getAlgorithm(),
                model.getModelVersion() == null ? "1.0.0" : model.getModelVersion(), metrics, model.getTrainedAt());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> catalog() {
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (var model : modelRepository.findAllMetadata()) {
            Map<String, Object> metrics = metrics(model);
            List<String> features = model.getFeatureColumns() == null || model.getFeatureColumns().isBlank()
                    ? (TrainedModelArtifact.ACTIVE_MODEL_ID.equals(model.getId())
                        ? List.of("attendance_percent", "average_marks_percent") : List.of())
                    : List.of(model.getFeatureColumns().split(","));
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("modelKey", model.getId());
            profile.put("label", Objects.requireNonNullElse(model.getLabel(),
                    TrainedModelArtifact.ACTIVE_MODEL_ID.equals(model.getId())
                            ? "Attendance and current marks → final score" : model.getId()));
            profile.put("features", features);
            profile.put("target", Objects.requireNonNullElse(model.getTargetColumn(),
                    TrainedModelArtifact.ACTIVE_MODEL_ID.equals(model.getId()) ? "final_score" : ""));
            profile.put("algorithm", model.getAlgorithm());
            profile.put("modelVersion", Objects.requireNonNullElse(model.getModelVersion(), "1.0.0"));
            profile.put("metrics", metrics);
            profile.put("trainedAt", model.getTrainedAt());
            profiles.add(profile);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("configured", ml.isConfigured());
        response.put("modelTrained", !profiles.isEmpty());
        response.put("models", profiles);
        return response;
    }

    @Transactional
    public Map<String, Object> train(List<org.springframework.web.multipart.MultipartFile> files) {
        Map<String, Object> result = ml.train(files);
        if (!(result.get("models") instanceof List<?> trainedProfiles) || trainedProfiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned no trained model profiles.");
        }
        List<Map<String, Object>> publicProfiles = new ArrayList<>();
        for (Object profileValue : trainedProfiles) {
            if (!(profileValue instanceof Map<?, ?> profile)
                    || !(profile.get("modelArtifact") instanceof String artifactBase64)
                    || !(profile.get("metrics") instanceof Map<?, ?> metrics)
                    || !(profile.get("features") instanceof List<?> features)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned an incomplete model profile.");
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
            String modelKey = requiredString(profile, "modelKey");
            TrainedModelArtifact model = new TrainedModelArtifact();
            model.setId(modelKey);
            model.setModelBytes(artifactBytes);
            model.setLabel(requiredString(profile, "label"));
            model.setFeatureColumns(features.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
            model.setTargetColumn(requiredString(profile, "target"));
            model.setAlgorithm(requiredString(profile, "algorithm"));
            model.setModelVersion(String.valueOf(profile.get("modelVersion") == null ? "1.2.0" : profile.get("modelVersion")));
            if (metrics.get("cvMae") instanceof Number cvMae) model.setCvMae(cvMae.doubleValue());
            model.setMae(number(metrics, "mae"));
            model.setRmse(number(metrics, "rmse"));
            model.setR2(number(metrics, "r2"));
            model.setDatasetRows(integer(metrics, "datasetRows"));
            model.setTrainRows(integer(metrics, "trainRows"));
            model.setTestRows(integer(metrics, "testRows"));
            model.setTrainedAt(profile.get("trainedAt") == null ? "" : String.valueOf(profile.get("trainedAt")));
            modelRepository.save(model);
            Map<String, Object> safeProfile = new LinkedHashMap<>();
            profile.forEach((key, value) -> {
                if (key instanceof String name && !name.equals("modelArtifact")) safeProfile.put(name, value);
            });
            publicProfiles.add(safeProfile);
        }
        return Map.of("modelTrained", true, "models", publicProfiles);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estimate(String modelKey, Map<String, Double> features) {
        TrainedModelArtifact model = modelRepository.findById(modelKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That prediction model has not been trained."));
        List<String> expected = model.getFeatureColumns() == null || model.getFeatureColumns().isBlank()
                ? List.of() : List.of(model.getFeatureColumns().split(","));
        if (expected.isEmpty() && TrainedModelArtifact.ACTIVE_MODEL_ID.equals(modelKey)) {
            expected = List.of("attendance_percent", "average_marks_percent");
        }
        if (features == null || expected.isEmpty() || !features.keySet().equals(new java.util.HashSet<>(expected))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide exactly these prediction inputs: " + String.join(", ", expected) + ".");
        }
        if (features.values().stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prediction inputs must be finite non-negative numbers.");
        }
        String encodedArtifact = Base64.getEncoder().encodeToString(model.getModelBytes());
        Map<String, Object> result = ml.predict(modelKey, features, encodedArtifact);
        if (!(result.get("predictions") instanceof List<?> predictions) || predictions.isEmpty()
                || !(predictions.getFirst() instanceof Number predicted)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned an invalid prediction.");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("modelKey", modelKey);
        response.put("label", model.getLabel());
        response.put("target", model.getTargetColumn());
        response.put("predictedValue", predicted.doubleValue());
        response.put("algorithm", model.getAlgorithm());
        response.put("modelVersion", model.getModelVersion());
        response.put("trainedAt", model.getTrainedAt());
        response.put("metrics", metrics(model));
        response.put("inputs", features);
        return response;
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
                storedModel.getAlgorithm(), storedModel.getModelVersion() == null ? "1.0.0" : storedModel.getModelVersion(), Instant.now().toString(),
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

    private static String requiredString(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) return text;
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service omitted " + key + " in a model profile.");
    }

    private static Map<String, Object> metrics(TrainedModelArtifact model) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mae", model.getMae());
        values.put("rmse", model.getRmse());
        values.put("r2", model.getR2());
        if (model.getCvMae() != null) values.put("cvMae", model.getCvMae());
        values.put("datasetRows", model.getDatasetRows());
        values.put("trainRows", model.getTrainRows());
        values.put("testRows", model.getTestRows());
        return values;
    }

    private static Map<String, Object> metrics(TrainedModelArtifactRepository.ModelMetadata model) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mae", model.getMae());
        values.put("rmse", model.getRmse());
        values.put("r2", model.getR2());
        if (model.getCvMae() != null) values.put("cvMae", model.getCvMae());
        values.put("datasetRows", model.getDatasetRows());
        values.put("trainRows", model.getTrainRows());
        values.put("testRows", model.getTestRows());
        return values;
    }
}
