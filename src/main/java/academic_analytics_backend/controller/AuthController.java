package academic_analytics_backend.controller;

import academic_analytics_backend.dto.AuthResponse;
import academic_analytics_backend.dto.AuthUserResponse;
import academic_analytics_backend.dto.CreateFacultyRequest;
import academic_analytics_backend.dto.LoginRequest;
import academic_analytics_backend.dto.StudentRegistrationRequest;
import academic_analytics_backend.dto.StudentProfileResponse;
import academic_analytics_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerStudent(@Valid @RequestBody StudentRegistrationRequest request) {
        return authService.registerStudent(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal String email) {
        return authService.currentUser(email);
    }

    @GetMapping("/me/student")
    public StudentProfileResponse myStudentProfile(@AuthenticationPrincipal String email) {
        return authService.currentStudentProfile(email);
    }

    @PostMapping("/faculty")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AuthUserResponse createFaculty(@Valid @RequestBody CreateFacultyRequest request) {
        return authService.createFaculty(request);
    }
}
