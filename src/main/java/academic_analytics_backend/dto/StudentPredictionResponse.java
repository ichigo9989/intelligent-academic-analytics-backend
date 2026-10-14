package academic_analytics_backend.dto;

import java.util.List;
import java.util.Map;

public record StudentPredictionResponse(Long studentId, String studentName,
                                        Double attendancePercent, Double averageMarksPercent,
                                        Double predictedFinalScore, String target,
                                        String algorithm, String modelVersion, String predictedAt,
                                        Map<String, Object> modelMetrics, String trainedAt,
                                        List<String> modelFeatures) {}
