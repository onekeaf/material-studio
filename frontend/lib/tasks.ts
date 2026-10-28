export type Task = {
  id: string;
  title: string;
  status: string;
  currentStage: string;
  stateVersion: number;
  createdAt: string;
  failureStage?: string;
  errorMessage?: string;
  resultUrl?: string;
  queryError?: string;
};
export function mergeTask(tasks: Task[], incoming: Task) {
  const old = tasks.find((t) => t.id === incoming.id);
  if (old && old.stateVersion > incoming.stateVersion) return tasks;
  const updated = old
    ? tasks.map((t) => (t.id === incoming.id ? incoming : t))
    : [incoming, ...tasks];
  return updated.sort((a, b) => {
    const first = Date.parse(a.createdAt),
      second = Date.parse(b.createdAt);
    return Number.isFinite(first) && Number.isFinite(second)
      ? second - first || b.id.localeCompare(a.id)
      : 0;
  });
}
export function queryFailed(tasks: Task[], id: string) {
  return tasks.map((t) =>
    t.id === id ? { ...t, queryError: "状态暂未更新，请恢复查询" } : t,
  );
}
export const pending = (t: Task) => ["QUEUED", "RUNNING"].includes(t.status);
