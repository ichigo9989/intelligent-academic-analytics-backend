package academic_analytics_backend.service;

import academic_analytics_backend.dto.AnalyticsSummary;
import academic_analytics_backend.entity.AttendanceStatus;
import academic_analytics_backend.repository.AttendanceRecordRepository;
import academic_analytics_backend.repository.MarkRecordRepository;
import academic_analytics_backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AnalyticsService {
    private static final double ATTENDANCE_RISK_THRESHOLD = 75.0;
    private static final double MARKS_RISK_THRESHOLD = 40.0;
    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final MarkRecordRepository marks;

    public AnalyticsService(StudentRepository students, AttendanceRecordRepository attendance,
                            MarkRecordRepository marks) {
        this.students = students;
        this.attendance = attendance;
        this.marks = marks;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary() {
        var studentRows = students.findAll();
        var attendanceRows = attendance.findAll();
        var markRows = marks.findAll();
        Map<Long, Totals> byStudent = new HashMap<>();
        for (var row : attendanceRows) {
            Totals totals = byStudent.computeIfAbsent(row.getStudent().getId(), ignored -> new Totals());
            totals.attendanceCount++;
            if (row.getStatus() == AttendanceStatus.PRESENT) totals.presentCount++;
        }
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        for (var row : markRows) {
            Totals totals = byStudent.computeIfAbsent(row.getStudent().getId(), ignored -> new Totals());
            totals.score = totals.score.add(row.getScore());
            totals.maxScore = totals.maxScore.add(row.getMaxScore());
            totalScore = totalScore.add(row.getScore());
            totalMax = totalMax.add(row.getMaxScore());
        }

        List<AnalyticsSummary.RiskStudent> atRisk = new ArrayList<>();
        for (var student : studentRows) {
            Totals totals = byStudent.get(student.getId());
            if (totals == null) continue;
            Double attendancePercent = totals.attendanceCount == 0 ? null
                    : percent(totals.presentCount, totals.attendanceCount);
            Double marksPercent = totals.maxScore.signum() == 0 ? null
                    : percent(totals.score, totals.maxScore);
            List<String> reasons = new ArrayList<>();
            if (attendancePercent != null && attendancePercent < ATTENDANCE_RISK_THRESHOLD) {
                reasons.add("Attendance is below " + (int) ATTENDANCE_RISK_THRESHOLD + "%.");
            }
            if (marksPercent != null && marksPercent < MARKS_RISK_THRESHOLD) {
                reasons.add("Marks average is below " + (int) MARKS_RISK_THRESHOLD + "%.");
            }
            if (reasons.isEmpty()) continue;
            boolean high = (attendancePercent != null && attendancePercent < 60.0)
                    || (marksPercent != null && marksPercent < 30.0);
            String recommendation = recommendation(attendancePercent, marksPercent);
            atRisk.add(new AnalyticsSummary.RiskStudent(student.getId(), student.getFullName(),
                    student.getRollNumber(), attendancePercent, marksPercent,
                    high ? "HIGH" : "MEDIUM", reasons, recommendation));
        }
        atRisk.sort(Comparator.comparingInt((AnalyticsSummary.RiskStudent student) ->
                        "HIGH".equals(student.riskLevel()) ? 0 : 1)
                .thenComparing(AnalyticsSummary.RiskStudent::fullName, String.CASE_INSENSITIVE_ORDER));
        long dataCount = byStudent.size();
        Double overallAttendance = attendanceRows.isEmpty() ? null
                : percent(attendanceRows.stream().filter(row -> row.getStatus() == AttendanceStatus.PRESENT).count(), attendanceRows.size());
        Double overallMarks = totalMax.signum() == 0 ? null : percent(totalScore, totalMax);
        return new AnalyticsSummary(studentRows.size(), attendanceRows.size(), markRows.size(), dataCount,
                atRisk.size(), overallAttendance, overallMarks,
                "At risk when recorded attendance is below 75% or weighted marks are below 40%. "
                        + "High risk means attendance below 60% or marks below 30%. Students without records are excluded.",
                List.copyOf(atRisk));
    }

    private static String recommendation(Double attendance, Double marks) {
        if (attendance != null && attendance < ATTENDANCE_RISK_THRESHOLD
                && marks != null && marks < MARKS_RISK_THRESHOLD) {
            return "Meet with the student, agree on an attendance recovery plan, and arrange subject-focused tutoring.";
        }
        if (attendance != null && attendance < ATTENDANCE_RISK_THRESHOLD) {
            return "Check in with the student about barriers to attendance and agree on a follow-up plan.";
        }
        return "Offer a subject review session and check progress on the next assessment.";
    }

    private static double percent(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP).doubleValue();
    }
    private static double percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP).doubleValue();
    }
    private static class Totals {
        long attendanceCount;
        long presentCount;
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
    }
}
