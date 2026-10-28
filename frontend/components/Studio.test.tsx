// @vitest-environment jsdom
import React from "react";
import {
  render,
  screen,
  cleanup,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { it, expect, vi, afterEach } from "vitest";
import { act } from "react";
import Studio from "./Studio";
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
// jsdom does not implement the browser's native modal methods.
Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
  configurable: true,
  value: function (this: HTMLDialogElement) {
    this.setAttribute("open", "");
  },
});
Object.defineProperty(HTMLDialogElement.prototype, "close", {
  configurable: true,
  value: function (this: HTMLDialogElement) {
    this.removeAttribute("open");
  },
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("retains existing history when recovering a failed reload also fails", async () => {
  setup([{ ...task, status: "SUCCEEDED", resultUrl: "/result.png" }]);
  render(<Studio initialView="history" />);
  await screen.findByText("任务甲");
  vi.mocked(fetch).mockImplementation(async () =>
    Response.json({ message: "服务暂不可用" }, { status: 503 }),
  );
  fireEvent.click(screen.getByRole("button", { name: "刷新状态" }));
  await screen.findByText("服务暂不可用");
  fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
  await screen.findByText("服务暂不可用");
  expect(await screen.findByText("任务甲")).toBeTruthy();
});
it("manual recovery queries the particular task, including records beyond page one", async () => {
  setup([task]);
  render(<Studio initialView="history" />);
  await screen.findByText("状态暂未更新，请恢复查询", {}, { timeout: 3000 });
  const mock = vi.mocked(fetch);
  mock.mockClear();
  mock.mockImplementation(async (url) =>
    url === "/api/tasks/task-a"
      ? Response.json({
          ...task,
          status: "SUCCEEDED",
          stateVersion: 3,
          resultUrl: "/result.png",
        })
      : Response.json([]),
  );
  fireEvent.click(screen.getByRole("button", { name: "恢复查询" }));
  await screen.findByText("已完成");
  expect(mock.mock.calls.some(([url]) => url === "/api/tasks/task-a")).toBe(
    true,
  );
  expect(mock.mock.calls.some(([url]) => url === "/api/me/tasks")).toBe(false);
  const count = mock.mock.calls.length;
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 2100));
  });
  expect(mock.mock.calls.length).toBe(count);
});
it("serializes identity changes and clears the previous identity records", async () => {
  setup([{ ...task, status: "SUCCEEDED" }]);
  render(<Studio initialView="history" />);
  await screen.findByText("任务甲");
  fireEvent.click(screen.getByRole("button", { name: /访客 / }));
  let release!: (r: Response) => void;
  const original = vi.mocked(fetch).getMockImplementation()!;
  const mock = vi.mocked(fetch).mockImplementation((url, options) =>
    url === "/api/dev/session/switch"
      ? new Promise((resolve) => {
          release = resolve;
        })
      : original(url, options),
  );
  fireEvent.click(screen.getByRole("button", { name: "访客 A" }));
  expect(screen.queryByText("任务甲")).toBeNull();
  expect(
    (screen.getByRole("button", { name: /访客 / }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  mock.mockImplementation(async (url) =>
    Response.json(url === "/api/session" ? { id: "new-user" } : []),
  );
  await act(async () => release(Response.json({ id: "new-user" })));
  expect(
    mock.mock.calls.filter(([url]) => url === "/api/dev/session/switch"),
  ).toHaveLength(1);
});
const template = {
  id: "portrait",
  title: "午后肖像",
  category: "人像",
  description: "上传一张照片",
  requiredImageCount: 1,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5242880,
  coverUrl: "/cover.png",
};
const task = {
  id: "task-a",
  title: "任务甲",
  status: "RUNNING",
  currentStage: "GENERATE",
  stateVersion: 2,
  createdAt: "2026-10-28T03:00:00Z",
};
function setup(tasks: unknown[] = []) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options?: RequestInit) => {
      if (url === "/api/session")
        return Response.json({ id: "visitor-a", demoEnabled: true });
      if (url === "/api/templates") return Response.json([template]);
      if (url.startsWith("/api/me/tasks")) return Response.json(tasks);
      if (url.startsWith("/api/tasks/task-"))
        return Response.json({ message: "暂时离线" }, { status: 503 });
      if (url === "/api/assets")
        return Response.json({ assetId: "asset-a", previewUrl: "/asset.png" });
      if (url === "/api/tasks" && options?.method === "POST")
        return Response.json({
          ...task,
          status: "QUEUED",
          currentStage: "WAITING",
        });
      return Response.json({});
    }),
  );
}
it("shows multiple tasks separately and preserves running state on query error", async () => {
  setup([
    task,
    {
      ...task,
      id: "task-b",
      title: "任务乙",
      status: "FAILED",
      currentStage: "FINALIZE",
      errorMessage: "整理失败",
    },
  ]);
  render(<Studio initialView="history" />);
  await screen.findByText("任务甲");
  await screen.findByText("任务乙");
  expect(screen.getByText("制作中")).toBeTruthy();
  expect(screen.getByText("整理失败")).toBeTruthy();
  await screen.findByText("状态暂未更新，请恢复查询", {}, { timeout: 3000 });
  expect(screen.getByText("制作中")).toBeTruthy();
  expect(screen.getAllByText("制作失败")).toHaveLength(1);
});
it("requires server-ready upload and restores submission after acceptance", async () => {
  setup();
  render(<Studio initialTemplate="portrait" />);
  const button = await screen.findByRole("button", { name: "开始制作" });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  const input = screen.getByLabelText("选择第1张图片");
  fireEvent.change(input, {
    target: { files: [new File(["image"], "demo.png", { type: "image/png" })] },
  });
  await waitFor(() =>
    expect((button as HTMLButtonElement).disabled).toBe(false),
  );
  fireEvent.click(button);
  await screen.findByText("任务甲");
  expect(screen.getByRole("button", { name: "开始制作" })).toBeTruthy();
  expect(screen.getByText("排队中")).toBeTruthy();
});
it("a late replaced upload cannot overwrite the selected server asset", async () => {
  setup();
  render(<Studio initialTemplate="portrait" />);
  const input = await screen.findByLabelText("选择第1张图片");
  const original = vi.mocked(fetch).getMockImplementation()!;
  const releases: Array<(r: Response) => void> = [];
  const mock = vi
    .mocked(fetch)
    .mockImplementation((url, options) =>
      url === "/api/assets"
        ? new Promise((resolve) => releases.push(resolve))
        : original(url, options),
    );
  fireEvent.change(input, {
    target: { files: [new File(["a"], "a.png", { type: "image/png" })] },
  });
  fireEvent.change(input, {
    target: { files: [new File(["b"], "b.png", { type: "image/png" })] },
  });
  await act(async () =>
    releases[1](
      Response.json({ assetId: "new-asset", previewUrl: "/new.png" }),
    ),
  );
  await act(async () =>
    releases[0](
      Response.json({ assetId: "old-asset", previewUrl: "/old.png" }),
    ),
  );
  expect(
    (screen.getByAltText("素材1") as HTMLImageElement).getAttribute("src"),
  ).toBe("/new.png");
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  const submission = mock.mock.calls.find(([url]) => url === "/api/tasks")!;
  expect(JSON.parse(submission[1]!.body as string).assetIds).toEqual([
    "new-asset",
  ]);
});
it("hides identity switching when the backend disables demo mode", async () => {
  setup();
  const original = vi.mocked(fetch).getMockImplementation()!;
  vi.mocked(fetch).mockImplementation((url, options) =>
    url === "/api/session"
      ? Promise.resolve(Response.json({ id: "visitor-a", demoEnabled: false }))
      : original(url, options),
  );
  render(<Studio />);
  await screen.findByText("把照片，变成新的故事。");
  fireEvent.click(screen.getByRole("button", { name: /访客 / }));
  expect(screen.getByRole("dialog", { name: "当前访客" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "访客 A" })).toBeNull();
});
it("cancels a manual history request when leaving the workspace", async () => {
  setup([{ ...task, status: "SUCCEEDED" }]);
  const { unmount } = render(<Studio initialView="history" />);
  await screen.findByText("任务甲");
  let signal: AbortSignal | undefined;
  vi.mocked(fetch).mockImplementation((_url, options) => {
    signal = options?.signal as AbortSignal;
    return new Promise(() => {});
  });
  fireEvent.click(screen.getByRole("button", { name: "刷新状态" }));
  expect(signal?.aborted).toBe(false);
  unmount();
  expect(signal?.aborted).toBe(true);
});
it("validates uploads against requirements returned by the template API", async () => {
  setup();
  const original = vi.mocked(fetch).getMockImplementation()!;
  const mock = vi
    .mocked(fetch)
    .mockImplementation((url, options) =>
      url === "/api/templates"
        ? Promise.resolve(
            Response.json([
              {
                ...template,
                acceptedMimeTypes: ["image/png"],
                maxFileSizeBytes: 16,
              },
            ]),
          )
        : original(url, options),
    );
  render(<Studio initialTemplate="portrait" />);
  const input = await screen.findByLabelText("选择第1张图片");
  expect(input.getAttribute("accept")).toBe("image/png");
  fireEvent.change(input, {
    target: {
      files: [new File(["jpg"], "picture.jpg", { type: "image/jpeg" })],
    },
  });
  expect(await screen.findByText("请选择模板支持的图片类型")).toBeTruthy();
  fireEvent.change(input, {
    target: {
      files: [new File(["x".repeat(17)], "picture.png", { type: "image/png" })],
    },
  });
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(mock.mock.calls.some(([url]) => url === "/api/assets")).toBe(false);
  expect(
    (screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
});
