package academic_analytics_backend.controller;

import academic_analytics_backend.entity.Student;
import academic_analytics_backend.service.StudentService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "${FRONTEND_ORIGIN:http://localhost:5173}")
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // Create a student
    @PostMapping
    public ResponseEntity<Student> createStudent(
            @Valid @RequestBody Student student) {

        Student savedStudent =
                studentService.createStudent(student);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedStudent);
    }

    // Get all students
    @GetMapping
    public List<Student> getAllStudents() {
        return studentService.getAllStudents();
    }

    // Get student by ID
    @GetMapping("/{id}")
    public Student getStudentById(@PathVariable Long id) {
        return studentService.getStudentById(id);
    }

    // Update student
    @PutMapping("/{id}")
    public Student updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody Student student) {

        return studentService.updateStudent(id, student);
    }

    // Delete student
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(
            @PathVariable Long id) {

        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}
