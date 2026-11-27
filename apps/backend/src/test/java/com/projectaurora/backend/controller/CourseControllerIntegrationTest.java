
package com.projectaurora.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

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
class CourseControllerIntegrationTest {

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
    private TestUser anotherInstructor;
    private TestUser admin;

    private record TestUser(
            String email,
            String token,
            Long userId) {
    }

    // ------------------------------------------
    // Test setup
    // ------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        student = registerUser(RoleType.STUDENT);
        instructor = registerUser(RoleType.INSTRUCTOR);
        anotherInstructor = registerUser(RoleType.INSTRUCTOR);
        admin = registerUser(RoleType.ADMIN);
    }

    // ------------------------------------------
    // Test cleanup
    // ------------------------------------------

    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbcTemplate.update(
                    "DELETE FROM courses WHERE instructor_id = ?",
                    userId
            );

            jdbcTemplate.update(
                    "DELETE FROM user_roles WHERE user_id = ?",
                    userId
            );

            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }

        createdUserIds.clear();
    }

    // ------------------------------------------
    // 1. Instructor creates a course
    // ------------------------------------------

    @Test
    void instructorCanCreateCourse() throws Exception {
        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "Java Programming",
                                        "Complete Java course"
                                )
                        ))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Java Programming"))
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.instructorId").value(instructor.userId()));
    }

    // ------------------------------------------
    // 2. Student cannot create a course
    // ------------------------------------------

    @Test
    void studentCannotCreateCourse() throws Exception {
        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(student))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "Unauthorized Course",
                                        "Student should not create this"
                                )
                        ))
        )
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------
    // 3. Unauthenticated user cannot browse
    // ------------------------------------------

    @Test
    void unauthenticatedUserCannotBrowseCourses() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------
    // 4. Student can see published courses only
    // ------------------------------------------

    @Test
    void studentCanOnlySeePublishedCourses() throws Exception {
        long publishedCourseId = createCourse(
                instructor,
                "Published Java Course"
        );

        long draftCourseId = createCourse(
                instructor,
                "Private Draft Course"
        );

        publishCourse(instructor, publishedCourseId);

        mockMvc.perform(
                get("/api/courses")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[*].title")
                        .value(hasItem("Published Java Course")))
                .andExpect(jsonPath("$.data.content[*].title")
                        .value(not(hasItem("Private Draft Course"))));

        mockMvc.perform(
                get("/api/courses/" + draftCourseId)
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------
    // 5. Instructor can update and publish own course
    // ------------------------------------------

    @Test
    void instructorCanUpdateAndPublishOwnCourse() throws Exception {
        long courseId = createCourse(
                instructor,
                "Original Course"
        );

        Map<String, String> updateRequest = Map.of(
                "title", "Updated Course",
                "description", "Updated course description"
        );

        mockMvc.perform(
                put("/api/courses/" + courseId)
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Course"));

        publishCourse(instructor, courseId);

        mockMvc.perform(
                get("/api/courses/" + courseId)
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));
    }

    // ------------------------------------------
    // 6. Another instructor cannot update course
    // ------------------------------------------

    @Test
    void instructorCannotUpdateAnotherInstructorsCourse() throws Exception {
        long courseId = createCourse(
                instructor,
                "Instructor One Course"
        );

        Map<String, String> updateRequest = Map.of(
                "title", "Unauthorized Update",
                "description", "This should be rejected"
        );

        mockMvc.perform(
                put("/api/courses/" + courseId)
                        .header("Authorization", bearer(anotherInstructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
        )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                patch("/api/courses/" + courseId + "/publish")
                        .header("Authorization", bearer(anotherInstructor))
        )
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------
    // 7. Admin can update and publish, but cannot create
    // ------------------------------------------

    @Test
    void adminCanUpdateAndPublishButCannotCreateCourses() throws Exception {
        long courseId = createCourse(
                instructor,
                "Instructor Course"
        );

        Map<String, String> updateRequest = Map.of(
                "title", "Admin Updated Course",
                "description", "Updated by administrator"
        );

        // Admin can update any course.
        mockMvc.perform(
                put("/api/courses/" + courseId)
                        .header("Authorization", bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Admin Updated Course"));

        // Admin can publish any course.
        publishCourse(admin, courseId);

        // Students can access the course after publication.
        mockMvc.perform(
                get("/api/courses/" + courseId)
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isOk());

        // Admin is not authorized to create courses.
        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(admin))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "Admin Created Course",
                                        "Created by admin"
                                )
                        ))
        )
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------
    // 8. Blank course title returns HTTP 400
    // ------------------------------------------

    @Test
    void blankCourseTitleReturnsBadRequest() throws Exception {
        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "   ",
                                        "Valid description"
                                )
                        ))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    // ------------------------------------------
    // 9. Course title exceeding 200 characters
    // ------------------------------------------

    @Test
    void oversizedCourseTitleReturnsBadRequest() throws Exception {
        String oversizedTitle = "A".repeat(201);

        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        oversizedTitle,
                                        "Valid description"
                                )
                        ))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------
    // 10. Course description exceeding 10000 characters
    // ------------------------------------------

    @Test
    void oversizedCourseDescriptionReturnsBadRequest() throws Exception {
        String oversizedDescription = "A".repeat(10001);

        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "Valid Course Title",
                                        oversizedDescription
                                )
                        ))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------
    // 11. Malformed JSON returns HTTP 400
    // ------------------------------------------

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\": \"Java Course\", \"description\": ")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or malformed request body."));
    }

    // ------------------------------------------
    // 12. Duplicate course title returns HTTP 409
    // ------------------------------------------

    @Test
    void duplicateCourseTitleReturnsConflict() throws Exception {
        createCourse(
                instructor,
                "Java Programming"
        );

        mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        "Java Programming",
                                        "Another Java course"
                                )
                        ))
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------
    // 13. Pagination
    // ------------------------------------------

    @Test
    void adminCanRetrievePaginatedCourses() throws Exception {
        Long existingCourseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courses",
                Long.class
        );

        if (existingCourseCount == null) {
            throw new AssertionError("Course count query returned null");
        }

        for (int i = 1; i <= 7; i++) {
            createCourse(
                    instructor,
                    "Pagination Course " + i
            );
        }

        long expectedTotalElements = existingCourseCount + 7;

        mockMvc.perform(
                get("/api/courses")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", bearer(admin))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(expectedTotalElements))
                .andExpect(jsonPath("$.data.totalPages")
                        .value((expectedTotalElements + 4) / 5));
    }

    // ------------------------------------------
    // 14. Invalid course ID
    // ------------------------------------------

    @Test
    void invalidCourseIdReturnsNotFound() throws Exception {
        mockMvc.perform(
                get("/api/courses/999999999")
                        .header("Authorization", bearer(student))
        )
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------
    // 15. Instructor can retrieve only their own courses
    // ------------------------------------------

    @Test
    void instructorCanRetrieveOnlyTheirOwnCourses() throws Exception {
        createCourse(instructor, "My Instructor Course");
        createCourse(anotherInstructor, "Another Instructor Course");

        mockMvc.perform(
                get("/api/courses/mine")
                        .header("Authorization", bearer(instructor))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[*].title")
                        .value(hasItem("My Instructor Course")))
                .andExpect(jsonPath("$.data.content[*].title")
                        .value(not(hasItem("Another Instructor Course"))));
    }

    // ------------------------------------------
    // 16. Updating to an existing title returns HTTP 409
    // ------------------------------------------

    @Test
    void updatingCourseToDuplicateTitleReturnsConflict() throws Exception {
        long firstCourseId = createCourse(instructor, "First Java Course");
        createCourse(instructor, "Second Java Course");

        Map<String, String> updateRequest = Map.of(
                "title", "Second Java Course",
                "description", "Attempt to reuse another course title"
        );

        mockMvc.perform(
                put("/api/courses/" + firstCourseId)
                        .header("Authorization", bearer(instructor))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ------------------------------------------
    // 17. Publishing an already-published course is idempotent
    // ------------------------------------------

    @Test
    void publishingAlreadyPublishedCourseRemainsSuccessful() throws Exception {
        long courseId = createCourse(instructor, "Idempotent Publish Course");

        publishCourse(instructor, courseId);

        mockMvc.perform(
                patch("/api/courses/" + courseId + "/publish")
                        .header("Authorization", bearer(instructor))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.published").value(true));
    }

    // ------------------------------------------
    // 18. Unsupported sort property returns HTTP 400
    // ------------------------------------------

    @Test
    void unsupportedSortPropertyReturnsBadRequest() throws Exception {
        mockMvc.perform(
                get("/api/courses")
                        .param("sort", "passwordHash,asc")
                        .header("Authorization", bearer(admin))
        )
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------
    // Helper methods
    // ------------------------------------------

    private TestUser registerUser(RoleType role) throws Exception {
        String email = "course-test-" + UUID.randomUUID() + "@example.com";

        Map<String, String> registrationRequest = Map.of(
                "firstName", "Course",
                "lastName", "Tester",
                "email", email,
                "password", "TestPassword@123"
        );

        MvcResult result = mockMvc.perform(
                post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest))
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

        // Registration assigns STUDENT by default.
        // Replace that role with the role required by this test.
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

    private Map<String, String> courseRequest(
            String title,
            String description) {

        return Map.of(
                "title", title,
                "description", description
        );
    }

    private long createCourse(
            TestUser user,
            String title) throws Exception {

        MvcResult result = mockMvc.perform(
                post("/api/courses")
                        .header("Authorization", bearer(user))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                courseRequest(
                                        title,
                                        "Integration test description"
                                )
                        ))
        )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        return response.path("data").path("id").asLong();
    }

    private void publishCourse(
            TestUser user,
            long courseId) throws Exception {

        mockMvc.perform(
                patch("/api/courses/" + courseId + "/publish")
                        .header("Authorization", bearer(user))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true));
    }

    private String bearer(TestUser user) {
        return "Bearer " + user.token();
    }
}
