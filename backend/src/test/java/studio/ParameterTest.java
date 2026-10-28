package studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ParameterTest {
  @TempDir Path directory;

  @Test
  void responsesUsePublicCamelCaseFieldsWithoutStorageDetails() throws Exception {
    Api api =
        new Api(
            mock(Store.class),
            mock(Pipeline.class),
            mock(PlatformTransactionManager.class),
            directory.toString(),
            true,
            new ObjectMapper());
    var fields =
        api.responseFields(
            Map.of(
                "task_id",
                "task",
                "started_at",
                "time",
                "visitor_id",
                "private-user",
                "result_path",
                "private-path",
                "simulation",
                "private-config",
                "status",
                "SUCCEEDED"));
    assertEquals("task", fields.get("taskId"));
    assertEquals("time", fields.get("startedAt"));
    assertFalse(fields.containsKey("visitorId"));
    assertFalse(fields.containsKey("resultPath"));
    assertFalse(fields.containsKey("simulation"));
    assertFalse(fields.containsKey("resultUrl"));
    var task = api.dto(Map.of("id", "task", "status", "SUCCEEDED"));
    assertEquals("/api/tasks/task/result", task.get("resultUrl"));
    var template =
        api.templateDto(
            Map.of(
                "id",
                "portrait",
                "title",
                "肖像",
                "category",
                "人像",
                "description",
                "示例",
                "required_image_count",
                1,
                "color",
                "private"));
    assertFalse(template.containsKey("required_image_count"));
    assertFalse(template.containsKey("color"));
    assertEquals(1, template.get("requiredImageCount"));
  }

  @Test
  void demoConfigurationAndSecureCookieAreConsistent() throws Exception {
    Store store = mock(Store.class);
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("required_image_count", 1));
    when(store.asset("asset", "owner")).thenReturn(Map.of("id", "asset"));
    Api api =
        new Api(
            store,
            mock(Pipeline.class),
            mock(PlatformTransactionManager.class),
            directory.toString(),
            false,
            new ObjectMapper());
    org.springframework.test.util.ReflectionTestUtils.setField(api, "cookieSecure", true);
    var response = new MockHttpServletResponse();
    assertEquals(false, api.identity("owner", "token", response).get("demoEnabled"));
    assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
    assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
    assertEquals(
        404,
        assertThrows(
                ResponseStatusException.class,
                () -> api.switchUser(Map.of("persona", "A"), response))
            .getStatusCode()
            .value());
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "token"));
    assertEquals(
        400,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    api.create(
                        new Api.Create(
                            "portrait", List.of("asset"), Map.of("failStage", "GENERATE")),
                        request))
            .getStatusCode()
            .value());
  }

  @Test
  void invalidSimulationAndIdentityAreRejectedBeforeCreatingTask() throws Exception {
    Store store = mock(Store.class);
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("required_image_count", 1));
    when(store.asset("asset", "owner")).thenReturn(Map.of("id", "asset"));
    Api api =
        new Api(
            store,
            mock(Pipeline.class),
            mock(PlatformTransactionManager.class),
            directory.toString(),
            true,
            new ObjectMapper());
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "cookie"));
    for (Map<String, Object> simulation :
        List.<Map<String, Object>>of(
            Map.of("delaysMs", "not-object"),
            Map.of("failStage", "WRONG"),
            Map.of("delaysMs", Map.of("generate", -1)),
            Map.of("delaysMs", Map.of("generate", 60001)),
            Map.of("delaysMs", Map.of("generate", 1.5)))) {
      var error =
          assertThrows(
              ResponseStatusException.class,
              () -> api.create(new Api.Create("portrait", List.of("asset"), simulation), request));
      assertEquals(400, error.getStatusCode().value());
    }
    assertEquals(
        400,
        assertThrows(
                ResponseStatusException.class,
                () -> api.switchUser(Map.of(), new MockHttpServletResponse()))
            .getStatusCode()
            .value());
    verify(store, never()).create(any(), any(), any(), any(), any());
  }
}
