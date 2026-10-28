import "./style.css";
export const metadata = {
  title: "拾光 · 素材制作",
  description: "挑选模板，留下你的画面",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
