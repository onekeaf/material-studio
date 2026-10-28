import type { Task } from "./tasks";
export function trackTasks(
  ids: string[],
  fetchTask: (id: string, signal: AbortSignal) => Promise<Task>,
  receive: (task: Task) => void,
  failed: (id: string) => void,
  isCurrent: () => boolean,
  interval = 2000,
) {
  const controller = new AbortController();
  let live = true;
  let timer: ReturnType<typeof setTimeout>;
  async function tick() {
    await Promise.all(
      ids.map(async (id) => {
        try {
          const task = await fetchTask(id, controller.signal);
          if (live && isCurrent()) receive(task);
        } catch {
          if (live && isCurrent()) failed(id);
        }
      }),
    );
    if (live && isCurrent()) timer = setTimeout(tick, interval);
  }
  if (ids.length) timer = setTimeout(tick, 800);
  return () => {
    live = false;
    controller.abort();
    clearTimeout(timer);
  };
}
