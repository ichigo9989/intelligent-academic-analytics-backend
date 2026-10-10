package academic_analytics_backend.service;

import academic_analytics_backend.dto.AttendanceRequest;
import academic_analytics_backend.dto.AttendanceResponse;
import academic_analytics_backend.dto.MarkRequest;
import academic_analytics_backend.dto.MarkResponse;
import academic_analytics_backend.entity.AttendanceRecord;
import academic_analytics_backend.entity.MarkRecord;
import academic_analytics_backend.entity.Student;
import academic_analytics_backend.repository.AttendanceRecordRepository;
import academic_analytics_backend.repository.MarkRecordRepository;
import academic_analytics_backend.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AcademicRecordService {
    private final AttendanceRecordRepository attendanceRepository;
    private final MarkRecordRepository markRepository;
    private final StudentRepository studentRepository;

    public AcademicRecordService(AttendanceRecordRepository attendanceRepository,
                                 MarkRecordRepository markRepository,
                                 StudentRepository studentRepository) {
        this.attendanceRepository = attendanceRepository;
        this.markRepository = markRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> attendance(Long studentId) {
        var records = studentId == null ? attendanceRepository.findAllByOrderByClassDateDesc()
                : attendanceRepository.findByStudent_IdOrderByClassDateDesc(studentId);
        return records.stream().map(AttendanceResponse::from).toList();
    }

    @Transactional
    public AttendanceResponse createAttendance(AttendanceRequest request) {
        AttendanceRecord record = new AttendanceRecord();
        apply(record, request);
        return AttendanceResponse.from(attendanceRepository.save(record));
    }

    @Transactional
    public AttendanceResponse updateAttendance(Long id, AttendanceRequest request) {
        AttendanceRecord record = attendanceRepository.findById(id)
                .orElseThrow(() -> notFound("Attendance record", id));
        apply(record, request);
        return AttendanceResponse.from(attendanceRepository.save(record));
    }

    @Transactional
    public void deleteAttendance(Long id) {
        if (!attendanceRepository.existsById(id)) throw notFound("Attendance record", id);
        attendanceRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MarkResponse> marks(Long studentId) {
        var records = studentId == null ? markRepository.findAllByOrderByAssessedAtDesc()
                : markRepository.findByStudent_IdOrderByAssessedAtDesc(studentId);
        return records.stream().map(MarkResponse::from).toList();
    }

    @Transactional
    public MarkResponse createMark(MarkRequest request) {
        MarkRecord record = new MarkRecord();
        apply(record, request);
        return MarkResponse.from(markRepository.save(record));
    }

    @Transactional
    public MarkResponse updateMark(Long id, MarkRequest request) {
        MarkRecord record = markRepository.findById(id)
                .orElseThrow(() -> notFound("Mark record", id));
        apply(record, request);
        return MarkResponse.from(markRepository.save(record));
    }

    @Transactional
    public void deleteMark(Long id) {
        if (!markRepository.existsById(id)) throw notFound("Mark record", id);
        markRepository.deleteById(id);
    }

    private void apply(AttendanceRecord record, AttendanceRequest request) {
        record.setStudent(findStudent(request.studentId()));
        record.setSubjectCode(clean(request.subjectCode()));
        record.setSubjectName(clean(request.subjectName()));
        record.setClassDate(request.classDate());
        record.setStatus(request.status());
    }

    private void apply(MarkRecord record, MarkRequest request) {
        if (request.score().compareTo(request.maxScore()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Score cannot exceed the maximum score.");
        }
        record.setStudent(findStudent(request.studentId()));
        record.setSubjectCode(clean(request.subjectCode()));
        record.setSubjectName(clean(request.subjectName()));
        record.setAssessmentName(clean(request.assessmentName()));
        record.setScore(request.score().setScale(2, java.math.RoundingMode.HALF_UP));
        record.setMaxScore(request.maxScore().setScale(2, java.math.RoundingMode.HALF_UP));
        record.setAssessedAt(request.assessedAt());
    }

    private Student findStudent(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> notFound("Student", id));
    }

    private static String clean(String value) { return value.trim(); }
    private static ResponseStatusException notFound(String label, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found: " + id);
    }
}
