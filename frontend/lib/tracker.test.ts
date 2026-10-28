import { it, expect, vi, afterEach } from "vitest";
import { trackTasks } from "./tracker";
import type { Task } from "./tasks";
afterEach(() => vi.useRealTimers());
it("does not poll an empty task collection", async () => {
  vi.useFakeTimers();
  const fetch = vi.fn();
  const stop = trackTasks([], fetch, vi.fn(), vi.fn(), () => true);
  await vi.advanceTimersByTimeAsync(10000);
  expect(fetch).not.toHaveBeenCalled();
  stop();
});
it("cleanup aborts and prevents late responses", async () => {
  vi.useFakeTimers();
  let resolve!: (t: Task) => void;
  const receive = vi.fn();
  const fetch = vi.fn((_id: string, signal: AbortSignal) => {
    expect(signal.aborted).toBe(false);
    return new Promise<Task>((r) => {
      resolve = r;
    });
  });
  const stop = trackTasks(["a"], fetch, receive, vi.fn(), () => true);
  await vi.advanceTimersByTimeAsync(800);
  stop();
  resolve({ id: "a" } as Task);
  await vi.advanceTimersByTimeAsync(5000);
  expect(receive).not.toHaveBeenCalled();
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("identity changes discard old responses", async () => {
  vi.useFakeTimers();
  let current = true;
  const receive = vi.fn();
  const stop = trackTasks(
    ["a"],
    async () => {
      current = false;
      return { id: "a" } as Task;
    },
    receive,
    vi.fn(),
    () => current,
  );
  await vi.advanceTimersByTimeAsync(10000);
  expect(receive).not.toHaveBeenCalled();
  stop();
});
it("query exception reports only that task and other tasks continue", async () => {
  vi.useFakeTimers();
  const receive = vi.fn(),
    failed = vi.fn();
  const stop = trackTasks(
    ["a", "b"],
    async (id) => {
      if (id === "a") throw Error("503");
      return { id } as Task;
    },
    receive,
    failed,
    () => true,
  );
  await vi.advanceTimersByTimeAsync(800);
  expect(failed).toHaveBeenCalledWith("a");
  expect(receive).toHaveBeenCalledWith({ id: "b" });
  stop();
});
