package academic_analytics_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFacultyRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName
) {
}
