"use client";
import { useEffect, useRef, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { trackTasks } from "../lib/tracker";
import { mergeTask, pending, queryFailed, type Task } from "../lib/tasks";
type Template = {
  id: string;
  title: string;
  category: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
  coverUrl: string;
};
type Slot = {
  assetId?: string;
  previewUrl?: string;
  loading?: boolean;
  error?: string;
};
async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const r = await fetch("/api" + path, { ...options, cache: "no-store" });
  if (!r.ok) {
    const error = await r.json().catch(() => ({ message: "请求失败，请重试" }));
    throw Error(error.message ?? error.detail ?? "请求失败，请重试");
  }
  return r.json();
}
const stage: Record<string, string> = {
  WAITING: "等待处理",
  ASSET_CHECK: "素材检查",
  GENERATE: "模拟生成",
  FINALIZE: "结果整理",
};
const status: Record<string, string> = {
  QUEUED: "排队中",
  RUNNING: "制作中",
  SUCCEEDED: "已完成",
  FAILED: "制作失败",
};
export default function Studio({
  initialTemplate,
  initialView = "templates",
}: {
  initialTemplate?: string;
  initialView?: string;
}) {
  const router = useRouter();
  const [templates, setTemplates] = useState<Template[]>([]),
    [category, setCategory] = useState("全部"),
    [selected, setSelected] = useState<Template | null>(null),
    [slots, setSlots] = useState<Slot[]>([]),
    [tasks, setTasks] = useState<Task[]>([]),
    [user, setUser] = useState(""),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true),
    [submitting, setSubmitting] = useState(false),
    [account, setAccount] = useState(false),
    [preview, setPreview] = useState(""),
    [view, setView] = useState(initialView),
    [historyOffset, setHistoryOffset] = useState(20),
    [hasMore, setHasMore] = useState(false),
    [loadingMore, setLoadingMore] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [demoEnabled, setDemoEnabled] = useState(false);
  const [trackingGeneration, setTrackingGeneration] = useState(0);
  const epoch = useRef(0),
    uploadSeq = useRef<number[]>([]),
    controllers = useRef(new Set<AbortController>()),
    inSubmit = useRef(false),
    selectionEpoch = useRef(0),
    identity = useRef(""),
    switchingRef = useRef(false),
    dialogRef = useRef<HTMLDialogElement>(null);
  const scopedApi = useCallback(async function request<T>(
    path: string,
    options: RequestInit = {},
  ) {
    const controller = new AbortController();
    controllers.current.add(controller);
    try {
      return await api<T>(path, { ...options, signal: controller.signal });
    } finally {
      controllers.current.delete(controller);
    }
  }, []);
  const initialize = useCallback(async () => {
    const generation = ++epoch.current;
    controllers.current.forEach((c) => c.abort());
    const controller = new AbortController();
    controllers.current.add(controller);
    setLoading(true);
    setLoadingMore(false);
    setError("");
    try {
      const session = await api<{ id: string; demoEnabled: boolean }>(
        "/session",
        {
          method: "POST",
          signal: controller.signal,
        },
      );
      if (generation !== epoch.current) return;
      if (identity.current && identity.current !== session.id) setTasks([]);
      identity.current = session.id;
      setUser(session.id);
      setDemoEnabled(session.demoEnabled === true);
      const [list, history, active] = await Promise.all([
        api<Template[]>("/templates", { signal: controller.signal }),
        api<Task[]>("/me/tasks", { signal: controller.signal }),
        api<Task[]>("/me/tasks/active", { signal: controller.signal }),
      ]);
      if (generation !== epoch.current) return;
      setTemplates(list);
      setTasks(active.reduce((all, t) => mergeTask(all, t), history));
      setHistoryOffset(20);
      setHasMore(history.length === 20);
      if (initialTemplate) {
        const t = list.find((t) => t.id === initialTemplate);
        if (!t) {
          setError("模板不存在，请返回模板列表");
        } else {
          setSelected(t);
          setSlots(Array.from({ length: t.requiredImageCount }, () => ({})));
          uploadSeq.current = Array(t.requiredImageCount).fill(0);
        }
      }
    } catch (e) {
      if (generation === epoch.current) setError((e as Error).message);
    } finally {
      controllers.current.delete(controller);
      if (generation === epoch.current) {
        setTrackingGeneration(generation);
        setLoading(false);
      }
    }
  }, [initialTemplate]);
  const invalidateRequests = useCallback(() => {
    epoch.current++;
    controllers.current.forEach((c) => c.abort());
  }, []);
  useEffect(() => {
    void initialize();
    return invalidateRequests;
  }, [initialize, invalidateRequests]);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog || (!account && !preview)) return;
    const previous = document.activeElement as HTMLElement | null;
    dialog.showModal();
    return () => {
      dialog.close();
      previous?.focus();
    };
  }, [account, preview]);
  const pendingIds = tasks
    .filter(pending)
    .map((t) => t.id)
    .sort()
    .join(",");
  useEffect(() => {
    if (loading || trackingGeneration !== epoch.current) return;
    const generation = trackingGeneration;
    return trackTasks(
      pendingIds ? pendingIds.split(",") : [],
      (id, signal) => api<Task>("/tasks/" + id, { signal }),
      (task) => setTasks((old) => mergeTask(old, task)),
      (id) => setTasks((old) => queryFailed(old, id)),
      () => generation === epoch.current,
    );
  }, [pendingIds, user, loading, trackingGeneration]);
  function choose(t: Template | null) {
    if (switchingRef.current) return;
    router.push(t ? "/templates/" + t.id : "/");
    selectionEpoch.current++;
    controllers.current.forEach((c) => c.abort());
    uploadSeq.current = Array(t?.requiredImageCount ?? 0).fill(0);
    setSelected(t);
    setSlots(Array.from({ length: t?.requiredImageCount ?? 0 }, () => ({})));
    setError("");
    setView("templates");
  }
  async function upload(index: number, file?: File) {
    if (!file || !selected) return;
    const seq = (uploadSeq.current[index] ?? 0) + 1;
    uploadSeq.current[index] = seq;
    const generation = epoch.current;
    const templateId = selected.id;
    const selection = selectionEpoch.current;
    function update(slot: Slot) {
      if (
        generation === epoch.current &&
        selection === selectionEpoch.current &&
        uploadSeq.current[index] === seq
      )
        setSlots((old) => old.map((s, i) => (i === index ? slot : s)));
    }
    if (!selected.acceptedMimeTypes.includes(file.type)) {
      update({ error: "请选择模板支持的图片类型" });
      return;
    }
    if (file.size > selected.maxFileSizeBytes) {
      update({
        error: `图片不能超过 ${selected.maxFileSizeBytes / 1048576} MiB`,
      });
      return;
    }
    update({ loading: true });
    const controller = new AbortController();
    controllers.current.add(controller);
    const form = new FormData();
    form.append("templateId", templateId);
    form.append("file", file);
    try {
      const result = await api<{ assetId: string; previewUrl: string }>(
        "/assets",
        { method: "POST", body: form, signal: controller.signal },
      );
      update(result);
    } catch (e) {
      if (!controller.signal.aborted) update({ error: (e as Error).message });
    } finally {
      controllers.current.delete(controller);
    }
  }
  async function submit() {
    if (
      inSubmit.current ||
      !selected ||
      !slots.every((s) => s.assetId && !s.loading)
    )
      return;
    inSubmit.current = true;
    setSubmitting(true);
    setError("");
    const generation = epoch.current;
    try {
      const task = await scopedApi<Task>("/tasks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          templateId: selected.id,
          assetIds: slots.map((s) => s.assetId),
        }),
      });
      if (generation === epoch.current) setTasks((old) => mergeTask(old, task));
    } catch (e) {
      if (generation === epoch.current)
        setError(
          (e as Error).message + "；提交结果不确定时请先刷新历史再新建。",
        );
    } finally {
      inSubmit.current = false;
      setSubmitting(false);
    }
  }
  async function refresh() {
    const generation = epoch.current;
    try {
      const history = await scopedApi<Task[]>("/me/tasks");
      if (generation === epoch.current)
        setTasks((old) => history.reduce((all, t) => mergeTask(all, t), old));
    } catch (e) {
      if (generation === epoch.current) setError((e as Error).message);
    }
  }
  async function refreshTask(id: string) {
    const generation = epoch.current;
    try {
      const task = await scopedApi<Task>("/tasks/" + id);
      if (generation === epoch.current) setTasks((old) => mergeTask(old, task));
    } catch {
      if (generation === epoch.current) setTasks((old) => queryFailed(old, id));
    }
  }
  async function switchUser(persona: string) {
    if (switchingRef.current) return;
    switchingRef.current = true;
    setSwitching(true);
    epoch.current++;
    const generation = epoch.current;
    controllers.current.forEach((c) => c.abort());
    setTasks([]);
    setSlots([]);
    setSelected(null);
    setAccount(false);
    const controller = new AbortController();
    controllers.current.add(controller);
    try {
      await api("/dev/session/switch", {
        method: "POST",
        signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ persona }),
      });
      if (generation !== epoch.current) return;
      await initialize();
    } catch (e) {
      if (generation === epoch.current) setError((e as Error).message);
    } finally {
      controllers.current.delete(controller);
      switchingRef.current = false;
      setSwitching(false);
    }
  }
  const cards = (
    <div className="task-grid">
      {tasks.length === 0 ? (
        <div className="empty">
          <h3>这里会留下你的作品</h3>
          <p>选择模板并上传照片，开始第一次制作。</p>
        </div>
      ) : (
        tasks.map((t) => (
          <article className={"task " + t.status.toLowerCase()} key={t.id}>
            <div className="task-body">
              {t.resultUrl ? (
                <button
                  className="result"
                  onClick={() => setPreview(t.resultUrl!)}
                  aria-label={"预览" + t.title}
                >
                  <img src={t.resultUrl} alt={t.title + "制作结果"} />
                </button>
              ) : (
                <div className="task-message">
                  <span className="status-dot" />
                  <h3>{status[t.status]}</h3>
                  <p>{stage[t.currentStage]}</p>
                  {t.errorMessage && (
                    <p className="failure">{t.errorMessage}</p>
                  )}
                </div>
              )}
            </div>
            <div className="task-meta">
              <strong>{t.title}</strong>
              {t.status === "SUCCEEDED" && <span>已完成</span>}
              <time>{new Date(t.createdAt).toLocaleString("zh-CN")}</time>
              <small>任务 {t.id.slice(0, 8)}</small>
              {t.queryError && (
                <div className="query-warning">
                  {t.queryError}
                  <button onClick={() => void refreshTask(t.id)}>
                    恢复查询
                  </button>
                </div>
              )}
            </div>
          </article>
        ))
      )}
      {hasMore && (
        <button
          disabled={loadingMore}
          onClick={async () => {
            const generation = epoch.current;
            setLoadingMore(true);
            try {
              const more = await scopedApi<Task[]>(
                `/me/tasks?limit=20&offset=${historyOffset}`,
              );
              if (generation !== epoch.current) return;
              setTasks((old) =>
                more
                  .reduce((all, t) => mergeTask(all, t), old)
                  .sort(
                    (a, b) =>
                      Date.parse(b.createdAt) - Date.parse(a.createdAt) ||
                      b.id.localeCompare(a.id),
                  ),
              );
              setHistoryOffset((old) => old + 20);
              setHasMore(more.length === 20);
            } catch (e) {
              if (generation === epoch.current) setError((e as Error).message);
            } finally {
              if (generation === epoch.current) setLoadingMore(false);
            }
          }}
        >
          {loadingMore ? "加载中…" : "更早的作品"}
        </button>
      )}
    </div>
  );
  return (
    <>
      <header>
        <button className="brand" onClick={() => choose(null)}>
          <span>拾</span>拾光 <small>素材工作室</small>
        </button>
        <nav>
          <button
            disabled={switching}
            onClick={() => {
              setView("history");
              router.push("/history");
              setSelected(null);
            }}
          >
            我的作品{" "}
            {tasks.filter(pending).length > 0 && (
              <b>{tasks.filter(pending).length}</b>
            )}
          </button>
          <button disabled={switching} onClick={() => setAccount(true)}>
            访客 {user.slice(-6) || "…"}
          </button>
        </nav>
      </header>
      <main>
        {error && (
          <div className="alert" role="alert">
            {error}
            <button onClick={() => void initialize()}>重新加载</button>
          </div>
        )}
        {loading ? (
          <div className="empty">正在准备你的工作室…</div>
        ) : view === "history" ? (
          <section className="panel history-full">
            <div className="panel-title">
              <h1>我的作品</h1>
              <button onClick={() => void refresh()}>刷新状态</button>
            </div>
            {cards}
          </section>
        ) : selected ? (
          <div className="workspace">
            <section className="panel maker">
              <button className="back" onClick={() => choose(null)}>
                ‹ 返回模板
              </button>
              <h1>{selected.title}</h1>
              <img
                className="example"
                src={selected.coverUrl}
                alt={selected.title + "示例"}
              />
              <p className="description">{selected.description}</p>
              <div className="upload-title">
                <h2>上传素材</h2>
                <small>
                  {selected.requiredImageCount} 张 ·{" "}
                  {selected.acceptedMimeTypes
                    .map((mime) => mime.replace("image/", "").toUpperCase())
                    .join(" / ")}{" "}
                  · 每张 {selected.maxFileSizeBytes / 1048576} MiB
                </small>
              </div>
              <div className={"slots" + (slots.length > 1 ? " multiple" : "")}>
                {slots.map((slot, i) => (
                  <div className="slot" key={i}>
                    {slot.previewUrl && (
                      <img src={slot.previewUrl} alt={"素材" + (i + 1)} />
                    )}
                    <label
                      className={slot.previewUrl ? "replace" : "upload-label"}
                    >
                      <input
                        aria-label={"选择第" + (i + 1) + "张图片"}
                        type="file"
                        accept={selected.acceptedMimeTypes.join(",")}
                        onChange={(e) => {
                          void upload(i, e.target.files?.[0]);
                          e.target.value = "";
                        }}
                      />
                      <span>
                        {slot.loading
                          ? "上传中…"
                          : slot.previewUrl
                            ? "替换图片"
                            : "＋ 选择图片 " + (i + 1)}
                      </span>
                    </label>
                    {slot.assetId && (
                      <button
                        className="remove"
                        aria-label={"移除第" + (i + 1) + "张图片"}
                        onClick={() => {
                          uploadSeq.current[i]++;
                          setSlots((old) =>
                            old.map((s, j) => (j === i ? {} : s)),
                          );
                        }}
                      >
                        移除
                      </button>
                    )}
                    {slot.error && (
                      <p role="alert" className="failure">
                        {slot.error}
                      </p>
                    )}
                  </div>
                ))}
              </div>
              <div className="submit-area">
                <button
                  className="primary"
                  disabled={
                    submitting ||
                    slots.length === 0 ||
                    !slots.every((s) => s.assetId && !s.loading)
                  }
                  onClick={() => void submit()}
                >
                  {submitting ? "正在提交…" : "开始制作"}
                </button>
                <small>制作在后台继续，你可以随时浏览其他模板。</small>
              </div>
            </section>
            <section className="panel history">
              <div className="panel-title">
                <h2>制作记录</h2>
                <button
                  onClick={() => void refresh()}
                  aria-label="刷新任务状态"
                >
                  ↻
                </button>
              </div>
              {cards}
            </section>
          </div>
        ) : (
          <>
            <div className="catalog-title">
              <div>
                <h1>模板素材</h1>
                <p>选择模板，上传照片开始制作</p>
              </div>
              <span>{templates.length} 个精选模板</span>
            </div>
            <div className="categories" aria-label="模板分类">
              {["全部", ...new Set(templates.map((t) => t.category))].map(
                (c) => (
                  <button
                    key={c}
                    aria-pressed={category === c}
                    className={category === c ? "active" : ""}
                    onClick={() => setCategory(c)}
                  >
                    {c}
                  </button>
                ),
              )}
            </div>
            <div className="template-grid">
              {templates
                .filter((t) => category === "全部" || t.category === category)
                .map((t) => (
                  <button
                    className="template-card"
                    onClick={() => choose(t)}
                    key={t.id}
                  >
                    <img src={t.coverUrl} alt="" />
                    <div className="cover-caption">
                      <span>
                        {t.category} · {t.requiredImageCount} 张照片
                      </span>
                      <h2>{t.title}</h2>
                    </div>
                  </button>
                ))}
            </div>
            {templates.length === 0 && !error && (
              <div className="empty">
                暂时没有模板
                <button onClick={() => void initialize()}>重新加载</button>
              </div>
            )}
          </>
        )}
      </main>
      {(account || preview) && (
        <dialog
          ref={dialogRef}
          onCancel={() => {
            setAccount(false);
            setPreview("");
          }}
          aria-label={account ? "当前访客" : "作品预览"}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              setAccount(false);
              setPreview("");
            }
          }}
        >
          <button
            autoFocus
            className="close"
            aria-label="关闭对话框"
            onClick={() => {
              setAccount(false);
              setPreview("");
            }}
          >
            ×
          </button>
          {account ? (
            <>
              <h2>当前访客</h2>
              <div className="identity">{user}</div>
              <p>作品与当前浏览器身份绑定。清除 Cookie 后无法找回匿名记录。</p>
              {demoEnabled && (
                <>
                  <h3>演示身份</h3>
                  <p>切换后只展示该访客的制作记录。</p>
                  <div className="switch">
                    <button onClick={() => void switchUser("A")}>访客 A</button>
                    <button onClick={() => void switchUser("B")}>访客 B</button>
                  </div>
                </>
              )}
            </>
          ) : (
            <img className="preview" src={preview} alt="制作结果大图" />
          )}
        </dialog>
      )}
      <footer>
        拾光素材工作室 <span>本地模拟制作 · 让创意先发生</span>
      </footer>
    </>
  );
}
