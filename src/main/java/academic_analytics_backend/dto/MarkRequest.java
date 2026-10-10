package academic_analytics_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MarkRequest(@NotNull @Positive Long studentId,
                          @NotBlank @Size(max = 30) String subjectCode,
                          @NotBlank @Size(max = 120) String subjectName,
                          @NotBlank @Size(max = 100) String assessmentName,
                          @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2) BigDecimal score,
                          @NotNull @DecimalMin(value = "0.01") @Digits(integer = 5, fraction = 2) BigDecimal maxScore,
                          @NotNull LocalDate assessedAt) {}
