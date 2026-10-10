package academic_analytics_backend.service;

import academic_analytics_backend.config.JwtTokenService;
import academic_analytics_backend.dto.AuthResponse;
import academic_analytics_backend.dto.AuthUserResponse;
import academic_analytics_backend.dto.CreateFacultyRequest;
import academic_analytics_backend.dto.LoginRequest;
import academic_analytics_backend.dto.StudentRegistrationRequest;
import academic_analytics_backend.dto.StudentProfileResponse;
import academic_analytics_backend.entity.AppUser;
import academic_analytics_backend.entity.Student;
import academic_analytics_backend.entity.UserRole;
import academic_analytics_backend.entity.Student;
import academic_analytics_backend.repository.AppUserRepository;
import academic_analytics_backend.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(AppUserRepository userRepository, StudentRepository studentRepository,
                       PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse registerStudent(StudentRegistrationRequest request) {
        validateBcryptPassword(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses that email.");
        }
        if (studentRepository.existsByRollNumberIgnoreCase(request.rollNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That roll number is already registered.");
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already associated with a student profile.");
        }

        Student student = new Student();
        student.setRollNumber(request.rollNumber().trim());
        student.setFullName(request.fullName().trim());
        student.setEmail(email);
        student.setDepartment(request.department().trim());
        student.setAcademicYear(request.academicYear());
        student.setSection(request.section().trim());
        student = studentRepository.save(student);

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(student.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.STUDENT);
        user.setStudent(student);
        user = userRepository.save(user);
        return issueResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        AppUser user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(AppUser::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return issueResponse(user);
    }

    @Transactional
    public AuthUserResponse createFaculty(CreateFacultyRequest request) {
        validateBcryptPassword(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses that email.");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.FACULTY);
        return AuthUserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(AppUser::isEnabled)
                .map(AuthUserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse currentStudentProfile(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        if (user.getRole() != UserRole.STUDENT || user.getStudent() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has no student profile.");
        }
        return StudentProfileResponse.from(user.getStudent());
    }

    @Transactional(readOnly = true)
    public Student currentStudentEntity(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
        if (user.getRole() != UserRole.STUDENT || user.getStudent() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has no student profile.");
        }
        return user.getStudent();
    }

    public AppUser createBootstrapAdmin(String email, String password) {
        AppUser admin = new AppUser();
        admin.setEmail(normalizeEmail(email));
        admin.setFullName("EduInsight Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        return userRepository.save(admin);
    }

    private AuthResponse issueResponse(AppUser user) {
        return new AuthResponse(tokenService.issue(user), AuthUserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateBcryptPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        }
    }
}
