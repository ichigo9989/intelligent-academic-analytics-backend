package academic_analytics_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "mark_records")
public class MarkRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Column(nullable = false, length = 30)
    private String subjectCode;
    @Column(nullable = false, length = 120)
    private String subjectName;
    @Column(nullable = false, length = 100)
    private String assessmentName;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal score;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal maxScore;
    @Column(nullable = false)
    private LocalDate assessedAt;

    public MarkRecord() {}
    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getAssessmentName() { return assessmentName; }
    public void setAssessmentName(String assessmentName) { this.assessmentName = assessmentName; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public LocalDate getAssessedAt() { return assessedAt; }
    public void setAssessedAt(LocalDate assessedAt) { this.assessedAt = assessedAt; }
}
