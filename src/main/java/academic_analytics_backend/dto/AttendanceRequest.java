package academic_analytics_backend.dto;

import academic_analytics_backend.entity.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AttendanceRequest(@NotNull @Positive Long studentId,
                                @NotBlank @Size(max = 30) String subjectCode,
                                @NotBlank @Size(max = 120) String subjectName,
                                @NotNull LocalDate classDate,
                                @NotNull AttendanceStatus status) {}
