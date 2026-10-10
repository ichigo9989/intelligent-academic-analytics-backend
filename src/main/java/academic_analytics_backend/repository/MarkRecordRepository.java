package academic_analytics_backend.repository;

import academic_analytics_backend.entity.MarkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarkRecordRepository extends JpaRepository<MarkRecord, Long> {
    List<MarkRecord> findAllByOrderByAssessedAtDesc();
    List<MarkRecord> findByStudent_IdOrderByAssessedAtDesc(Long studentId);
    void deleteByStudent_Id(Long studentId);
}
