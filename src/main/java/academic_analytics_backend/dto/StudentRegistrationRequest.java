package academic_analytics_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentRegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 50) String rollNumber,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 100) String department,
        @NotNull @Min(1) @Max(8) Integer academicYear,
        @NotBlank @Size(max = 10) String section
) {
}
