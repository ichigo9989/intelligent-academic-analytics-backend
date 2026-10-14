package academic_analytics_backend.dto;

import java.util.Map;

public record ModelStatusResponse(boolean configured, boolean modelTrained,
                                  String algorithm, String modelVersion,
                                  Map<String, Object> modelMetrics, String trainedAt) {}
