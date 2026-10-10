package academic_analytics_backend.repository;

import academic_analytics_backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByRollNumberIgnoreCase(String rollNumber);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRollNumberIgnoreCaseAndIdNot(String rollNumber, Long id);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
