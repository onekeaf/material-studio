package studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ParameterTest {
  @TempDir Path directory;

  @Test
  void opaqueCookieResolvesItsOwnerWithoutStoringTheRawToken() throws Exception {
    Store store = mock(Store.class);
    Map<String, String> identities = new HashMap<>();
    when(store.visitor(anyString())).thenAnswer(call -> identities.get(call.getArgument(0)));
    doAnswer(call -> {
      identities.put(call.getArgument(1), call.getArgument(0));
      return null;
    }).when(store).visitorAdd(anyString(), anyString());
    Api api = new Api(store, mock(Pipeline.class), mock(PlatformTransactionManager.class),
        directory.toString(), true, new ObjectMapper());
    var response = new MockHttpServletResponse();
    api.identity("owner", "opaque-token", response);
    assertFalse(identities.containsKey("opaque-token"));
    assertEquals(64, identities.keySet().iterator().next().length());
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "opaque-token"));
    assertEquals("owner", api.owner(request));
    request.setCookies(new Cookie("studio", "unrelated-token"));
    assertEquals(401, assertThrows(ResponseStatusException.class,
        () -> api.owner(request)).getStatusCode().value());
  }

  @Test
  void rejectedQueuePersistsFailureAfterCreatingTask() throws Exception {
    Store store = mock(Store.class);
    Pipeline pipeline = mock(Pipeline.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("required_image_count", 1, "title", "肖像"));
    when(store.asset("asset", "owner")).thenReturn(Map.of("id", "asset"));
    when(store.task(anyString(), eq("owner"))).thenReturn(Map.of("id", "queued", "status", "FAILED"));
    doThrow(new java.util.concurrent.RejectedExecutionException()).when(pipeline).submit(anyString(), any(), any());
    Api api = new Api(store, pipeline, transactions, directory.toString(), true, new ObjectMapper());
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "cookie"));
    var response = api.create(new Api.Create("portrait", List.of("asset"), null), request);
    assertEquals(202, response.getStatusCode().value());
    verify(store).update(anyString(), eq("FAILED"), eq("WAITING"), contains("队列已满"), isNull());
    verify(transactions).commit(any());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 59999, 60000})
  void delayAcceptsInclusiveBounds(long value) throws Exception {
    Api api = new Api(mock(Store.class), mock(Pipeline.class), mock(PlatformTransactionManager.class), directory.toString(), true, new ObjectMapper());
    assertEquals(value, api.delay(Map.of("delaysMs", Map.of("generate", value)), "generate", 6000));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "60001", "1.5", "1000"})
  void delayRejectsOutOfRangeFractionAndString(String input) throws Exception {
    Api api = new Api(mock(Store.class), mock(Pipeline.class), mock(PlatformTransactionManager.class), directory.toString(), true, new ObjectMapper());
    Object value = input.equals("1000") ? input : Double.valueOf(input);
    assertEquals(400, assertThrows(ResponseStatusException.class,
        () -> api.delay(Map.of("delaysMs", Map.of("generate", value)), "generate", 6000)).getStatusCode().value());
  }

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
