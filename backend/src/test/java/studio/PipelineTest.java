package studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;

class PipelineTest {
  @Test
  void asynchronousSubmissionDoesNotWaitForStage() throws Exception {
    Store store = mock(Store.class);
    Pipeline pipeline = new Pipeline(store);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var finished = new java.util.concurrent.CountDownLatch(1);
    try {
      pipeline.submit(
          "async",
          new Pipeline.Simulation(null, 0, 0, 0),
          stage -> {
            if (stage.equals("ASSET_CHECK")) {
              entered.countDown();
              release.await();
            }
            if (stage.equals("FINALIZE")) finished.countDown();
          });
      assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
      verify(store, never()).update(eq("async"), eq("SUCCEEDED"), any(), any(), any());
      release.countDown();
      assertTrue(finished.await(3, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      release.countDown();
      pipeline.close();
    }
  }

  @Test
  void taskFailureDoesNotAffectNextTask() {
    Store store = mock(Store.class);
    Pipeline pipeline = new Pipeline(store);
    pipeline.execute("failed", new Pipeline.Simulation("GENERATE", 0, 0, 0), stage -> {});
    pipeline.execute("success", new Pipeline.Simulation(null, 0, 0, 0), stage -> {});
    verify(store).update("success", "SUCCEEDED", "FINALIZE", null, "success.png");
    verify(store, never()).update(eq("failed"), eq("SUCCEEDED"), any(), any(), any());
    pipeline.close();
  }

  @Test
  void successOrder() {
    Store store = mock(Store.class);
    Pipeline p = new Pipeline(store);
    List<String> calls = new ArrayList<>();
    p.execute("a", new Pipeline.Simulation(null, 0, 0, 0), calls::add);
    assertEquals(Pipeline.STAGES, calls);
    verify(store).update("a", "SUCCEEDED", "FINALIZE", null, "a.png");
    p.close();
  }

  @Test
  void everyFailureStopsLaterSteps() {
    for (int i = 0; i < 3; i++) {
      Store s = mock(Store.class);
      Pipeline p = new Pipeline(s);
      List<String> calls = new ArrayList<>();
      String stage = Pipeline.STAGES.get(i);
      p.execute("a", new Pipeline.Simulation(stage, 0, 0, 0), calls::add);
      assertEquals(Pipeline.STAGES.subList(0, i), calls);
      verify(s).update(eq("a"), eq("FAILED"), eq(stage), anyString(), isNull());
      verify(s, never()).update(eq("a"), eq("SUCCEEDED"), anyString(), any(), any());
      p.close();
    }
  }

  @Test
  void realExceptionStops() {
    Store s = mock(Store.class);
    Pipeline p = new Pipeline(s);
    Pipeline.Steps steps = mock(Pipeline.Steps.class);
    try {
      doThrow(new IllegalStateException("broken")).when(steps).run("GENERATE");
      p.execute("a", new Pipeline.Simulation(null, 0, 0, 0), steps);
      verify(steps, never()).run("FINALIZE");
    } catch (Exception e) {
      fail(e);
    }
    p.close();
  }
}
