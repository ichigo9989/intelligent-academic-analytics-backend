package academic_analytics_backend.dto;

import java.util.List;

public record AnalyticsSummary(long studentCount, long attendanceEntryCount, long markEntryCount,
                               long studentsWithAcademicData, long atRiskCount,
                               Double averageAttendancePercent, Double averageMarksPercent,
                               String riskPolicy, List<RiskStudent> atRiskStudents) {
    public record RiskStudent(Long studentId, String fullName, String rollNumber,
                              Double attendancePercent, Double averageMarksPercent,
                              String riskLevel, List<String> reasons, String recommendation) {}
}
