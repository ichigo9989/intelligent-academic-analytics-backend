package academic_analytics_backend.dto;

import academic_analytics_backend.entity.Student;

public record StudentProfileResponse(
        Long id,
        String rollNumber,
        String fullName,
        String email,
        String department,
        Integer academicYear,
        String section
) {
    public static StudentProfileResponse from(Student student) {
        return new StudentProfileResponse(
                student.getId(), student.getRollNumber(), student.getFullName(), student.getEmail(),
                student.getDepartment(), student.getAcademicYear(), student.getSection()
        );
    }
}
