package academic_analytics_backend.controller;

import academic_analytics_backend.dto.AttendanceRequest;
import academic_analytics_backend.dto.AttendanceResponse;
import academic_analytics_backend.service.AcademicRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
public class AttendanceController {
    private final AcademicRecordService records;
    public AttendanceController(AcademicRecordService records) { this.records = records; }

    @GetMapping public List<AttendanceResponse> list() { return records.attendance(null); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse create(@Valid @RequestBody AttendanceRequest request) { return records.createAttendance(request); }
    @PutMapping("/{id}")
    public AttendanceResponse update(@PathVariable Long id, @Valid @RequestBody AttendanceRequest request) { return records.updateAttendance(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { records.deleteAttendance(id); }
}
