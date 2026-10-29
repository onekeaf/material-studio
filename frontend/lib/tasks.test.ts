import { describe, it, expect } from "vitest";
import { mergeTask, queryFailed, pending, type Task } from "./tasks";
const a: Task = {
  id: "a",
  title: "a",
  status: "RUNNING",
  currentStage: "GENERATE",
  stateVersion: 2,
  createdAt: "",
};
const b: Task = { ...a, id: "b" };
describe("task isolation", () => {
  it("restored older active tasks do not jump above recent history", () => {
    const newer = { ...a, createdAt: "2026-10-28T05:00:00Z" };
    const older = { ...b, createdAt: "2026-10-28T04:00:00Z" };
    expect(mergeTask([newer], older).map((t) => t.id)).toEqual(["a", "b"]);
  });
  it("updates only matching task", () => {
    const tasks = mergeTask([a, b], {
      ...a,
      status: "SUCCEEDED",
      stateVersion: 3,
    });
    expect(tasks[1]).toEqual(b);
    expect(pending(tasks[0])).toBe(false);
  });
  it("query failure preserves task state", () => {
    const tasks = queryFailed([a, b], "a");
    expect(tasks[0].status).toBe("RUNNING");
    expect(tasks[0].queryError).toBeTruthy();
    expect(tasks[1].queryError).toBeUndefined();
  });
  it("ignores older response", () => {
    expect(mergeTask([a], { ...a, stateVersion: 1, status: "QUEUED" })).toEqual(
      [a],
    );
  });
  it("server failure is terminal", () =>
    expect(pending({ ...a, status: "FAILED" })).toBe(false));
  it("successful recovery removes the query warning without changing other tasks", () => {
    const failedQuery = queryFailed([a, b], "a");
    const recovered = mergeTask(failedQuery, { ...a, status: "SUCCEEDED", stateVersion: 3, resultUrl: "/result" });
    expect(recovered[0].queryError).toBeUndefined();
    expect(recovered[0].resultUrl).toBe("/result");
    expect(recovered[1]).toEqual(b);
  });
  it("a late running snapshot cannot replace a newer terminal state", () => {
    const done = { ...a, status: "SUCCEEDED", stateVersion: 5, resultUrl: "/result" };
    expect(mergeTask([done, b], { ...a, stateVersion: 3 })).toEqual([done, b]);
  });
  it("a query failure for an unknown task does not change other cards", () => {
    expect(queryFailed([a, b], "missing")).toEqual([a, b]);
  });
  it.each(["SUCCEEDED", "FAILED"])("%s is terminal", (status) => {
    expect(pending({ ...a, status })).toBe(false);
  });
});
