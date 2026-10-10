package academic_analytics_backend.dto;

import java.util.Map;

public record ModelStatusResponse(boolean configured, boolean modelTrained,
                                  Map<String, Object> modelMetrics, String trainedAt) {}
