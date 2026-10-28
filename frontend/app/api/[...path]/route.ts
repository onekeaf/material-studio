import { NextRequest } from "next/server";
export const dynamic = "force-dynamic";
async function proxy(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  const headers = new Headers();
  for (const name of ["cookie", "content-type"]) {
    const value = req.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    const response = await fetch(
      `${process.env.BACKEND_URL ?? "http://backend:8080"}/api/${path.join("/")}${req.nextUrl.search}`,
      {
        method: req.method,
        headers,
        body: req.method === "GET" ? undefined : await req.arrayBuffer(),
        cache: "no-store",
        signal: req.signal,
      },
    );
    const out = new Headers();
    for (const name of ["content-type", "set-cookie"]) {
      const v = response.headers.get(name);
      if (v) out.set(name, v);
    }
    out.set("Cache-Control", "private, no-store");
    return new Response(response.body, {
      status: response.status,
      headers: out,
    });
  } catch {
    return Response.json(
      { message: "后台连接暂不可用，请稍后恢复查询" },
      { status: 503 },
    );
  }
}
export { proxy as GET, proxy as POST };
