package academic_analytics_backend.controller;

import academic_analytics_backend.dto.MarkRequest;
import academic_analytics_backend.dto.MarkResponse;
import academic_analytics_backend.service.AcademicRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/marks")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
public class MarkController {
    private final AcademicRecordService records;
    public MarkController(AcademicRecordService records) { this.records = records; }

    @GetMapping public List<MarkResponse> list() { return records.marks(null); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MarkResponse create(@Valid @RequestBody MarkRequest request) { return records.createMark(request); }
    @PutMapping("/{id}")
    public MarkResponse update(@PathVariable Long id, @Valid @RequestBody MarkRequest request) { return records.updateMark(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { records.deleteMark(id); }
}
