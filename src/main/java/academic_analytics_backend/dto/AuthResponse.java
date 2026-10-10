package academic_analytics_backend.dto;

public record AuthResponse(String accessToken, String tokenType, AuthUserResponse user) {
    public AuthResponse(String accessToken, AuthUserResponse user) {
        this(accessToken, "Bearer", user);
    }
}
