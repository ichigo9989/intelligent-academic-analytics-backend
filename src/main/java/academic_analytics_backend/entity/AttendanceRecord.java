package academic_analytics_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Column(nullable = false, length = 30)
    private String subjectCode;
    @Column(nullable = false, length = 120)
    private String subjectName;
    @Column(nullable = false)
    private LocalDate classDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    public AttendanceRecord() {}
    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public LocalDate getClassDate() { return classDate; }
    public void setClassDate(LocalDate classDate) { this.classDate = classDate; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
}
