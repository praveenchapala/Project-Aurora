
package com.projectaurora.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.projectaurora.backend.entity.User;
import com.projectaurora.backend.enums.RoleType;
import com.projectaurora.backend.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class EnrollmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    private TestUser student;
    private TestUser instructor;
    private TestUser admin;

    private record TestUser(
            String email,
            String token,
            Long userId) {
    }

    @BeforeEach
    void setUp() throws Exception {
        student = registerUser(RoleType.STUDENT);
        instructor = registerUser(RoleType.INSTRUCTOR);
        admin = registerUser(RoleType.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {

            // Delete enrollments where this user is the student.
            jdbcTemplate.update(
                    """
                    DELETE FROM enrollments
                    WHERE student_id = ?
                    """,
                    userId
            );

            // Delete enrollments for courses created by this instructor.
            jdbcTemplate.update(
                    """
                    DELETE FROM enrollments
                    WHERE course_id IN (
                        SELECT id
                        FROM courses
                        WHERE instructor_id = ?
                    )
                    """,
                    userId
            );

            // Delete courses created by this instructor.
            jdbcTemplate.update(
                    "DELETE FROM courses WHERE instructor_id = ?",
                    userId
            );

            // Delete user role mappings.
            jdbcTemplate.update(
                    "DELETE FROM user_roles WHERE user_id = ?",
                    userId
            );

            // Delete the user.
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }

        createdUserIds.clear();
    }

    @Test
    void studentCanEnrollInPublishedCourse() throws Exception {
        long courseId = createPublishedCourse();

        mockMvc.perform(
                post("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courseId").value(courseId));
    }

    @Test
    void duplicateEnrollmentReturnsConflict() throws Exception {
        long courseId = createPublishedCourse();

        enrollStudent(courseId);

        mockMvc.perform(
                post("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void enrollmentInUnpublishedCourseReturnsBadRequest()
            throws Exception {

        long courseId = createCourse(
                instructor,
                "Unpublished Enrollment Course"
        );

        mockMvc.perform(
                post("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void enrollmentForMissingCourseReturnsNotFound()
            throws Exception {

        mockMvc.perform(
                post("/api/courses/999999999/enroll")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthenticatedUserCannotEnroll() throws Exception {
        mockMvc.perform(
                post("/api/courses/999999999/enroll")
        )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void instructorCannotEnroll() throws Exception {
        long courseId = createPublishedCourse();

        mockMvc.perform(
                post("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", bearer(instructor))
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanRetrieveOwnEnrollments() throws Exception {
        long courseId = createPublishedCourse();

        enrollStudent(courseId);

        mockMvc.perform(
                get("/api/enrollments/me")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].courseId")
                        .value(courseId));
    }

    @Test
    void studentCanCheckEnrollmentStatus() throws Exception {
        long courseId = createPublishedCourse();

        enrollStudent(courseId);

        mockMvc.perform(
                get("/api/courses/" + courseId + "/enrollment")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void studentGetsFalseWhenNotEnrolled() throws Exception {
        long courseId = createPublishedCourse();

        mockMvc.perform(
                get("/api/courses/" + courseId + "/enrollment")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    private TestUser registerUser(RoleType role) throws Exception {
        String email =
                "enrollment-test-" + UUID.randomUUID() + "@example.com";

        Map<String, String> registrationRequest = Map.of(
                "firstName", "Enrollment",
                "lastName", "Tester",
                "email", email,
                "password", "TestPassword@123"
        );

        MvcResult result = mockMvc.perform(
                post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                registrationRequest
                        ))
        )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        String token = response.path("accessToken").asText();

        User user = userRepository.findByEmail(email)
                .orElseThrow();

        Long userId = user.getId();
        createdUserIds.add(userId);

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE user_roles
                SET role_id = (
                    SELECT id
                    FROM roles
                    WHERE role_name = ?
                )
                WHERE user_id = ?
                """,
                role.name(),
                userId
        );

        assertEquals(
                1,
                updatedRows,
                "Expected exactly one user role to be updated"
        );

        return new TestUser(email, token, userId);
    }

    private long createCourse(
            TestUser user,
            String title) throws Exception {

        Map<String, String> request = Map.of(
                "title", title,
                "description", "Enrollment integration test course"
        );

        MvcResult result = mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(user))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return response.path("data").path("id").asLong();
    }

    private long createPublishedCourse() throws Exception {
        long courseId = createCourse(
                instructor,
                "Published Enrollment Course " + UUID.randomUUID()
        );

        // Publish the course using PATCH.
        mockMvc.perform(
                patch("/api/courses/" + courseId + "/publish")
                        .header("Authorization", bearer(instructor))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));

        // Verify that the published course is accessible to the student.
        mockMvc.perform(
                get("/api/courses/" + courseId)
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));

        return courseId;
    }

    private void enrollStudent(long courseId) throws Exception {
        mockMvc.perform(
                post("/api/courses/" + courseId + "/enroll")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isCreated());
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }
}
