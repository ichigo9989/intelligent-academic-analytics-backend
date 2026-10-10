package academic_analytics_backend.service;

import academic_analytics_backend.entity.Student;
import academic_analytics_backend.repository.StudentRepository;
import academic_analytics_backend.repository.AppUserRepository;
import academic_analytics_backend.repository.AttendanceRecordRepository;
import academic_analytics_backend.repository.MarkRecordRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final AppUserRepository appUserRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final MarkRecordRepository markRepository;

    public StudentService(StudentRepository studentRepository, AppUserRepository appUserRepository,
                          AttendanceRecordRepository attendanceRepository, MarkRecordRepository markRepository) {
        this.studentRepository = studentRepository;
        this.appUserRepository = appUserRepository;
        this.attendanceRepository = attendanceRepository;
        this.markRepository = markRepository;
    }

    // Create a student
    public Student createStudent(Student student) {
        normalize(student);
        if (studentRepository.existsByRollNumberIgnoreCase(student.getRollNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That roll number is already in use.");
        }
        if (student.getEmail() != null && studentRepository.existsByEmailIgnoreCase(student.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That student email is already in use.");
        }
        return studentRepository.save(student);
    }

    // Get all students
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    // Get a student by ID
    public Student getStudentById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Student not found: " + id
                        ));
    }

    // Update a student
    public Student updateStudent(Long id, Student updatedStudent) {
        normalize(updatedStudent);

        Student existingStudent = studentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Student not found: " + id
                        ));

        if (studentRepository.existsByRollNumberIgnoreCaseAndIdNot(updatedStudent.getRollNumber(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That roll number is already in use.");
        }
        if (updatedStudent.getEmail() != null
                && studentRepository.existsByEmailIgnoreCaseAndIdNot(updatedStudent.getEmail(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That student email is already in use.");
        }

        existingStudent.setRollNumber(updatedStudent.getRollNumber());
        existingStudent.setFullName(updatedStudent.getFullName());
        existingStudent.setEmail(updatedStudent.getEmail());
        existingStudent.setDepartment(updatedStudent.getDepartment());
        existingStudent.setAcademicYear(updatedStudent.getAcademicYear());
        existingStudent.setSection(updatedStudent.getSection());

        return studentRepository.save(existingStudent);
    }

    // Delete a student
    @Transactional
    public void deleteStudent(Long id) {

        if (!studentRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Student not found: " + id
            );
        }

        attendanceRepository.deleteByStudent_Id(id);
        markRepository.deleteByStudent_Id(id);
        appUserRepository.deleteByStudent_Id(id);
        studentRepository.deleteById(id);
    }

    private static void normalize(Student student) {
        student.setRollNumber(student.getRollNumber().trim());
        student.setFullName(student.getFullName().trim());
        student.setEmail(student.getEmail() == null || student.getEmail().isBlank()
                ? null : student.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        student.setDepartment(student.getDepartment() == null ? null : student.getDepartment().trim());
        student.setSection(student.getSection() == null ? null : student.getSection().trim());
    }
}
