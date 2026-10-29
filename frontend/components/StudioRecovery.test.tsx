// @vitest-environment jsdom
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { it, expect, vi, afterEach } from "vitest";
import Studio from "./Studio";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const template = {
  id: "portrait", title: "恢复测试模板", category: "人像", description: "测试",
  requiredImageCount: 1, acceptedMimeTypes: ["image/png"],
  maxFileSizeBytes: 5242880, coverUrl: "/cover.png",
};
const task = {
  id: "same-task", title: "恢复中的任务", templateId: "portrait", status: "RUNNING",
  currentStage: "GENERATE", stateVersion: 2, createdAt: "2026-10-29T01:00:00Z",
};
function backend() {
  let terminal = "", failHistory = false, failSession = false, sessions = 0, polls = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (url === "/api/session") {
      sessions++;
      if (failSession) {
        failSession = false;
        return Response.json({ message: "重新加载暂不可用" }, { status: 503 });
      }
      return Response.json({ id: "same-user", demoEnabled: false });
    }
    if (url === "/api/templates") return Response.json([template]);
    if (url === "/api/me/tasks" && failHistory) {
      failHistory = false;
      return Response.json({ message: "历史查询暂不可用" }, { status: 503 });
    }
    if (url.startsWith("/api/me/tasks")) return Response.json([task]);
    if (url === "/api/tasks/same-task") {
      polls++;
      return Response.json(!terminal ? task : {
        ...task, status: terminal, stateVersion: 4,
        ...(terminal === "SUCCEEDED" ? { resultUrl: "/result.png" } : {
          failureStage: "GENERATE", errorMessage: "生成失败的业务原因",
        }),
      });
    }
    throw new Error("Unexpected request " + url);
  }));
  return {
    finish: (status = "SUCCEEDED") => { terminal = status; },
    failHistory: () => { failHistory = true; },
    failSession: () => { failSession = true; },
    sessions: () => sessions, polls: () => polls,
  };
}
async function openRecovery(server: ReturnType<typeof backend>) {
  render(<Studio initialView="history" />);
  await screen.findByText("恢复中的任务");
  server.failHistory();
  fireEvent.click(screen.getByRole("button", { name: "刷新状态" }));
  await screen.findByText("历史查询暂不可用");
  fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
}

it.each(["SUCCEEDED", "FAILED"])("same-user reload keeps tracking until %s", async (status) => {
  const server = backend();
  await openRecovery(server);
  await waitFor(() => expect(server.sessions()).toBe(2));
  await screen.findByRole("button", { name: "刷新状态" });
  server.finish(status);
  expect(await screen.findByText(status === "SUCCEEDED" ? "已完成" : "生成失败的业务原因", {}, { timeout: 3500 })).toBeTruthy();
});

it("continues polling a still-running task after reinitialization", async () => {
  const server = backend();
  await openRecovery(server);
  await waitFor(() => expect(server.sessions()).toBe(2));
  await screen.findByRole("button", { name: "刷新状态" });
  await waitFor(() => expect(server.polls()).toBeGreaterThan(0), { timeout: 2000 });
  expect(screen.getByText("制作中")).toBeTruthy();
  server.finish();
  expect(await screen.findByText("已完成", {}, { timeout: 3500 })).toBeTruthy();
});

it("can recover again after initialization itself temporarily fails", async () => {
  const retryServer = backend();
  render(<Studio initialView="history" />);
  await screen.findByText("恢复中的任务");
  retryServer.failHistory();
  fireEvent.click(screen.getByRole("button", { name: "刷新状态" }));
  await screen.findByText("历史查询暂不可用");
  retryServer.failSession();
  fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
  await screen.findByText("重新加载暂不可用");
  expect(screen.getByText("恢复中的任务")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
  await waitFor(() => expect(retryServer.sessions()).toBe(3));
  await screen.findByRole("button", { name: "刷新状态" });
  retryServer.finish();
  expect(await screen.findByText("已完成", {}, { timeout: 3500 })).toBeTruthy();
});
