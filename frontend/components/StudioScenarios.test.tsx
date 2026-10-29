// @vitest-environment jsdom
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { it, expect, vi, afterEach } from "vitest";
import Studio from "./Studio";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
const templates = [
  { id: "one", title: "单图模板", category: "人像", description: "上传一张", requiredImageCount: 1, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 5, coverUrl: "/one" },
  { id: "two", title: "双图模板", category: "旅行", description: "上传两张", requiredImageCount: 2, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 5, coverUrl: "/two" },
];
const task = { id: "task-1", templateId: "one", title: "单图任务", status: "QUEUED", currentStage: "WAITING", stateVersion: 0, createdAt: "2026-10-29T00:00:00Z" };
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => { resolve = r; });
  return { promise, resolve };
}
function setup(overrides: (url: string, options?: RequestInit) => Promise<Response> | undefined = () => undefined) {
  let uploadNumber = 0;
  vi.stubGlobal("fetch", vi.fn((url: string, options?: RequestInit) => {
    const custom = overrides(url, options);
    if (custom) return custom;
    if (url === "/api/session") return Promise.resolve(Response.json({ id: "u", demoEnabled: false }));
    if (url === "/api/templates") return Promise.resolve(Response.json(templates));
    if (url.startsWith("/api/me/tasks")) return Promise.resolve(Response.json([]));
    if (url === "/api/assets") {
      uploadNumber++;
      return Promise.resolve(Response.json({ assetId: `asset-${uploadNumber}`, previewUrl: `/asset-${uploadNumber}` }));
    }
    if (url === "/api/tasks") return Promise.resolve(Response.json(task));
    if (url.startsWith("/api/tasks/")) return Promise.resolve(Response.json(task));
    throw Error(`unexpected ${url}`);
  }));
}
function upload(n: number, contents = "1", type = "image/png") {
  fireEvent.change(screen.getByLabelText(`选择第${n}张图片`), {
    target: { files: [new File([contents], "image.png", { type })] },
  });
}

it("filters categories and opens the selected template with its required slots", async () => {
  setup(); render(<Studio />);
  await screen.findByRole("button", { name: /单图模板/ });
  fireEvent.click(screen.getByRole("button", { name: "旅行" }));
  expect(screen.queryByRole("button", { name: /单图模板/ })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: /双图模板/ }));
  expect(screen.getAllByLabelText(/选择第\d张图片/)).toHaveLength(2);
  expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(true);
});

it("shows missing template, empty list and load error without crashing", async () => {
  setup();
  const view = render(<Studio initialTemplate="missing" />);
  expect(await screen.findByText("模板不存在，请返回模板列表")).toBeTruthy();
  view.unmount();
  setup((url) => url === "/api/templates" ? Promise.resolve(Response.json([])) : undefined);
  const emptyView = render(<Studio />);
  expect(await screen.findByText("暂时没有模板")).toBeTruthy();
  emptyView.unmount();
  setup((url) => url === "/api/templates" ? Promise.resolve(Response.json({ message: "模板服务离线" }, { status: 503 })) : undefined);
  render(<Studio />);
  expect(await screen.findByText("模板服务离线")).toBeTruthy();
  expect(screen.queryByText("暂时没有模板")).toBeNull();
  expect(screen.getByRole("button", { name: "重新加载" })).toBeTruthy();
});

it("requires both server uploads and disables submission while either is pending", async () => {
  const second = deferred<Response>(); let calls = 0;
  setup((url) => url === "/api/assets" && ++calls === 2 ? second.promise : undefined);
  render(<Studio initialTemplate="two" />);
  await screen.findByLabelText("选择第1张图片");
  upload(1); await screen.findByAltText("素材1");
  expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(true);
  upload(2); expect(screen.getByText("上传中…")).toBeTruthy();
  expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(true);
  second.resolve(Response.json({ assetId: "second", previewUrl: "/second" }));
  await screen.findByAltText("素材2");
  expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "移除第1张图片" }));
  expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(true);
});

it.each([["oversized", "123456", "image/png", "图片不能超过"], ["wrong type", "1", "image/gif", "请选择模板支持的图片类型"]])(
  "rejects %s locally without an upload request", async (_name, contents, type, error) => {
    setup(); render(<Studio initialTemplate="one" />);
    await screen.findByLabelText("选择第1张图片"); upload(1, contents, type);
    expect(screen.getByText(new RegExp(error))).toBeTruthy();
    expect(vi.mocked(fetch).mock.calls.some(([url]) => url === "/api/assets")).toBe(false);
  },
);

it("prevents duplicate in-flight submits, then allows a new task after acceptance", async () => {
  const accepted = deferred<Response>();
  setup((url) => url === "/api/tasks" ? accepted.promise : undefined);
  render(<Studio initialTemplate="one" />);
  await screen.findByLabelText("选择第1张图片"); upload(1); await screen.findByAltText("素材1");
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  fireEvent.click(screen.getByRole("button", { name: "正在提交…" }));
  expect(vi.mocked(fetch).mock.calls.filter(([url]) => url === "/api/tasks")).toHaveLength(1);
  accepted.resolve(Response.json(task));
  await screen.findByText("单图任务");
  await waitFor(() => expect((screen.getByRole("button", { name: "开始制作" }) as HTMLButtonElement).disabled).toBe(false));
});

it("shows uncertain submission failure without falsely creating a failed task", async () => {
  setup((url) => url === "/api/tasks" ? Promise.resolve(Response.json({ message: "连接中断" }, { status: 503 })) : undefined);
  render(<Studio initialTemplate="one" />);
  await screen.findByLabelText("选择第1张图片"); upload(1); await screen.findByAltText("素材1");
  fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
  expect(await screen.findByText(/提交结果不确定时请先刷新历史/)).toBeTruthy();
  expect(screen.queryByText("制作失败")).toBeNull();
  expect(screen.queryByText("单图任务")).toBeNull();
});
