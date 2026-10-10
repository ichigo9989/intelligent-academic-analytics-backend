package academic_analytics_backend.dto;

import academic_analytics_backend.entity.AttendanceRecord;
import academic_analytics_backend.entity.AttendanceStatus;
import java.time.LocalDate;

public record AttendanceResponse(Long id, Long studentId, String studentName, String rollNumber,
                                 String subjectCode, String subjectName, LocalDate classDate,
                                 AttendanceStatus status) {
    public static AttendanceResponse from(AttendanceRecord record) {
        return new AttendanceResponse(record.getId(), record.getStudent().getId(),
                record.getStudent().getFullName(), record.getStudent().getRollNumber(),
                record.getSubjectCode(), record.getSubjectName(), record.getClassDate(), record.getStatus());
    }
}
