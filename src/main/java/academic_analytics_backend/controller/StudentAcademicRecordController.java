package academic_analytics_backend.controller;

import academic_analytics_backend.dto.AttendanceResponse;
import academic_analytics_backend.dto.MarkResponse;
import academic_analytics_backend.service.AcademicRecordService;
import academic_analytics_backend.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/auth/me")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAcademicRecordController {
    private final AuthService authService;
    private final AcademicRecordService records;
    public StudentAcademicRecordController(AuthService authService, AcademicRecordService records) {
        this.authService = authService;
        this.records = records;
    }
    @GetMapping("/attendance")
    public List<AttendanceResponse> attendance(@AuthenticationPrincipal String email) {
        return records.attendance(authService.currentStudentEntity(email).getId());
    }
    @GetMapping("/marks")
    public List<MarkResponse> marks(@AuthenticationPrincipal String email) {
        return records.marks(authService.currentStudentEntity(email).getId());
    }
}
