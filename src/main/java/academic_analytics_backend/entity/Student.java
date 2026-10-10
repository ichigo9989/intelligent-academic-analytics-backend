package academic_analytics_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank @Size(max = 50)
    private String rollNumber;

    @Column(nullable = false)
    @NotBlank @Size(max = 150)
    private String fullName;

    @Column(unique = true)
    @Email @Size(max = 254)
    private String email;

    @Size(max = 100)
    private String department;
    @Min(1) @Max(8)
    private Integer academicYear;
    @Size(max = 10)
    private String section;

    // Required by JPA
    public Student() {
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(Integer academicYear) {
        this.academicYear = academicYear;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }
}
