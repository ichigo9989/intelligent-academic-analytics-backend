package academic_analytics_backend.dto;

import academic_analytics_backend.entity.AppUser;

public record AuthUserResponse(Long id, String email, String fullName, String role, Long studentId) {
    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                user.getStudent() == null ? null : user.getStudent().getId()
        );
    }
}
