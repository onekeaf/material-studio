package studio;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class Pipeline {
  public static final List<String> STAGES = List.of("ASSET_CHECK", "GENERATE", "FINALIZE");

  public record Simulation(String failStage, long assetCheck, long generate, long finalizeMs) {
    public long delay(int i) {
      return new long[] {assetCheck, generate, finalizeMs}[i];
    }
  }

  public interface Steps {
    void run(String stage) throws Exception;
  }

  private final Store store;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));

  public Pipeline(Store store) {
    this.store = store;
  }

  public void submit(String id, Simulation config, Steps action) {
    executor.execute(() -> execute(id, config, action));
  }

  public void execute(String id, Simulation config, Steps action) {
    for (int i = 0; i < STAGES.size(); i++) {
      String stage = STAGES.get(i);
      try {
        store.update(id, "RUNNING", stage, null, null);
        store.step(id, stage, "RUNNING", null);
        if (config.delay(i) > 0) Thread.sleep(config.delay(i));
        if (stage.equals(config.failStage()))
          throw new IllegalStateException(
              Map.of("ASSET_CHECK", "素材检查", "GENERATE", "模拟生成", "FINALIZE", "结果整理").get(stage)
                  + "未能完成：已触发演示失败，请新建任务");
        action.run(stage);
        store.step(id, stage, "SUCCEEDED", null);
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        String message = Objects.toString(e.getMessage(), "制作暂时无法完成");
        store.step(id, stage, "FAILED", message);
        store.update(id, "FAILED", stage, message, null);
        return;
      }
    }
    store.update(id, "SUCCEEDED", "FINALIZE", null, id + ".png");
  }

  @PreDestroy
  void close() {
    executor.shutdownNow();
  }
}
