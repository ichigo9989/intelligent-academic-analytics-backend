package academic_analytics_backend.dto;

import academic_analytics_backend.entity.MarkRecord;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MarkResponse(Long id, Long studentId, String studentName, String rollNumber,
                           String subjectCode, String subjectName, String assessmentName,
                           BigDecimal score, BigDecimal maxScore, LocalDate assessedAt) {
    public static MarkResponse from(MarkRecord record) {
        return new MarkResponse(record.getId(), record.getStudent().getId(),
                record.getStudent().getFullName(), record.getStudent().getRollNumber(),
                record.getSubjectCode(), record.getSubjectName(), record.getAssessmentName(),
                record.getScore(), record.getMaxScore(), record.getAssessedAt());
    }
}
