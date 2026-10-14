package academic_analytics_backend;

import academic_analytics_backend.config.JwtTokenService;
import academic_analytics_backend.entity.AppUser;
import academic_analytics_backend.entity.UserRole;
import academic_analytics_backend.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "FRONTEND_ORIGIN=https://academic-test.netlify.app")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcademicAnalyticsBackendApplicationTests {

    private static final String FRONTEND_ORIGIN = "https://academic-test.netlify.app";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService tokenService;

    @Test
    void contextLoads() {
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/students")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
    }

    @Test
    void corsRejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/students")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentRecordsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void studentCanRegisterSignInAndOnlyReadTheirOwnProfile() throws Exception {
        String email = "student-" + UUID.randomUUID() + "@example.edu";
        String rollNumber = "TEST-" + UUID.randomUUID();
        String password = "SecureTestPassword-2026";
        String registration = """
                {"email":"%s","password":"%s","rollNumber":"%s","fullName":"Test Student","department":"AIML","academicYear":2,"section":"A"}
                """.formatted(email, password, rollNumber);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registration))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("STUDENT"));

        String login = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = jsonMapper.readTree(response).get("accessToken").asString();

        mockMvc.perform(get("/api/auth/me/student")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollNumber").value(rollNumber));
        mockMvc.perform(get("/api/students")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me/marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void facultyCanManageRecordsAndAnalyticsUsesSavedData() throws Exception {
        AppUser admin = new AppUser();
        admin.setEmail("admin-" + UUID.randomUUID() + "@example.edu");
        admin.setFullName("Test Admin");
        admin.setPasswordHash(passwordEncoder.encode("SecureAdminPassword-2026"));
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);
        String adminToken = tokenService.issue(admin);

        String facultyEmail = "faculty-" + UUID.randomUUID() + "@example.edu";
        String facultyRequest = """
                {"email":"%s","password":"SecureFacultyPassword-2026","fullName":"Test Faculty"}
                """.formatted(facultyEmail);
        mockMvc.perform(post("/api/auth/faculty")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType("application/json").content(facultyRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FACULTY"));

        String facultyLogin = """
                {"email":"%s","password":"SecureFacultyPassword-2026"}
                """.formatted(facultyEmail);
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json").content(facultyLogin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String facultyToken = jsonMapper.readTree(loginResponse).get("accessToken").asString();

        String studentJson = """
                {"rollNumber":"TEST-%s","fullName":"Risk Test Student","email":null,"department":"AIML","academicYear":2,"section":"A"}
                """.formatted(UUID.randomUUID());
        String studentResponse = mockMvc.perform(post("/api/students")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken)
                        .contentType("application/json").content(studentJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long studentId = jsonMapper.readTree(studentResponse).get("id").asLong();

        for (String statusValue : new String[]{"PRESENT", "ABSENT"}) {
            String attendanceJson = """
                    {"studentId":%d,"subjectCode":"CS201","subjectName":"Data Structures","classDate":"2026-10-09","status":"%s"}
                    """.formatted(studentId, statusValue);
            mockMvc.perform(post("/api/attendance")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken)
                            .contentType("application/json").content(attendanceJson))
                    .andExpect(status().isCreated());
        }
        String markJson = """
                {"studentId":%d,"subjectCode":"CS201","subjectName":"Data Structures","assessmentName":"Midterm","score":30,"maxScore":100,"assessedAt":"2026-10-09"}
                """.formatted(studentId);
        mockMvc.perform(post("/api/marks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken)
                        .contentType("application/json").content(markJson))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageAttendancePercent").value(50.0))
                .andExpect(jsonPath("$.averageMarksPercent").value(30.0))
                .andExpect(jsonPath("$.atRiskStudents[0].riskLevel").value("HIGH"));

        mockMvc.perform(get("/api/predictions/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelTrained").value(false));
        mockMvc.perform(post("/api/predictions/" + studentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + facultyToken))
                .andExpect(status().isServiceUnavailable());
    }
}
