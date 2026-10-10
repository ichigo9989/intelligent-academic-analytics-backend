package academic_analytics_backend.repository;

import academic_analytics_backend.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findAllByOrderByClassDateDesc();
    List<AttendanceRecord> findByStudent_IdOrderByClassDateDesc(Long studentId);
    void deleteByStudent_Id(Long studentId);
}
