# 拾光素材工作室

一个可独立运行的素材制作网站。用户浏览模板、上传图片并提交任务后，可以继续浏览或创建其他任务，在个人历史中查看进度、失败原因和结果。Java 后台依次执行“素材检查 → 模拟生成 → 结果整理”，状态和历史保存在 MySQL。生成器使用可控延迟、指定阶段失败和本地示意图片，不调用真实 AI 服务，也不实际加工上传照片。

## 1. 技术版本与项目结构

| 部分 | 版本与用途 |
| --- | --- |
| 前端 | Next.js 16.4.3 App Router、React / React DOM 19.3.1、TypeScript 5.9.3 |
| 前端运行及检查 | Node.js 22.16.0、ESLint 9.39.1、Vitest 5.0.5；其他测试依赖精确版本见 frontend/package.json |
| 后端 | Java 21、Spring Boot 3.5.7、MyBatis Spring Boot Starter 3.0.4 |
| 后端构建及测试 | Maven 3.9.9、Spring Boot Test、JUnit、Mockito；传递版本由 Spring Boot BOM 管理 |
| 数据库 | MySQL 8.4 镜像，命名数据卷保存数据 |
| 容器验收工具 | Python 3.12.10，使用标准库访问接口 |

前端精确依赖及安装结果由 `frontend/package-lock.json` 锁定；后端版本由 `backend/pom.xml` 声明。Dockerfile 指定 Node / Maven / Java 镜像版本，构建与运行均在容器中完成，所选组合已进行本地容器验证。

```text
frontend/
  app/                         列表、详情、历史路由与同源 API 代理
  components/Studio.tsx        上传、提交、任务卡、个人记录与预览
  lib/                         任务快照合并、查询异常和可取消轮询
backend/
  src/main/java/studio/
    Api.java                   身份、模板、上传、任务受理及查询接口
    Store.java                 MyBatis 映射与 MySQL 状态持久化
    Pipeline.java              有界线程池与三步顺序执行
    Artwork.java               自制封面与结果插画
  src/test/                    顺序、失败中断、异步、上传与参数测试
database/                      数据库结构与模板种子
demo-assets/                   可直接上传的自制 PNG 示例
scripts/                       成功、阶段失败、耗时和接口验收脚本
docs/                          验收证据、工作记录、AI 和来源说明
.github/workflows/ci.yml        push / pull_request 检查
compose.yaml                   应用、数据库与容器测试入口
.env.example                   无真实凭据的本地配置示例
```

浏览器访问 Next.js 的同源 `/api/*`；Next.js 仅转发请求、文件体、Cookie 和响应。Java 提供模板、素材、任务和个人记录业务，通过 MyBatis 连接 MySQL。上传文件和结果图片由 Java 读写，不由 Next.js 执行业务或持久化。

## 2. 构建、测试与启动

需要可运行 Linux 容器的 Docker 和 Docker Compose。首次拉取基础镜像及依赖需要网络；宿主不需要安装 Node、JDK、Maven 或 MySQL，也不需要在宿主执行项目脚本。

在本 README 所在目录执行：

```sh
docker compose up --build
```

也可以后台启动并等待健康检查：

```sh
docker compose up --build -d --wait
docker compose ps
```

访问 [http://localhost:3000](http://localhost:3000)。页面分别为 `/` 模板列表、`/templates/{id}` 详情与制作、`/history` 个人历史。仅前端端口暴露给宿主；Java 和 MySQL 通过 Compose 内网通信。

构建会执行前端 lint、类型检查、测试、生产构建，以及 Java 测试和打包。需要单独或重复执行检查时使用以下容器入口：

```sh
docker compose run --build --rm frontend-check
docker compose run --build --rm backend-check
docker compose run --rm smoke
```

| 入口 | 容器内执行内容 |
| --- | --- |
| frontend-check | ESLint、tsc --noEmit、Vitest、Next 生产构建；依赖安装使用 npm ci |
| backend-check | mvn -B verify，执行 Java 测试并打包 |
| smoke | 访问运行中的 Next / Java / MySQL，验证上传、受理、三步执行、失败中断、多任务、隔离与结果 |

`smoke` 使用默认 `DEMO_ENABLED=true`，会创建测试访客、上传素材和制作任务，写入数据库及文件卷，并更新 `docs/evidence/smoke.json`；测试图片保存在脚本内存中，不改写随源码提供的 demo.png。单元测试不要求数据库运行；真实业务验收需要先启动三服务。构建缓存命中时不会重复执行 Dockerfile 中的旧层，独立 check 入口仍会实际运行检查命令。

查看日志或停止服务：

```sh
docker compose logs backend
docker compose down
```

`down` 保留数据卷。需要清空一次性测试环境并重新初始化时才使用 `docker compose down -v`；该操作会删除本 Compose 项目的数据库、上传和结果文件。

## 3. 配置、数据库初始化与素材

默认配置无需准备真实凭据，也无需创建 `.env`。需要修改时，将 `.env.example` 复制为 `.env` 并调整：

| Compose 可覆盖项 | 默认值 | 说明 |
| --- | --- | --- |
| FRONTEND_PORT | 3000 | 浏览器访问端口 |
| DB_PASSWORD | studio-local | 本地演示数据库账号密码，Java 与 MySQL 使用相同值 |
| DEMO_ENABLED | true | 允许请求指定模拟参数和演示访客 A / B；关闭后隐藏切换按钮并拒绝对应开发请求 |
| COOKIE_SECURE | false | 本地 HTTP 使用 false；通过 HTTPS 部署时设为 true |

以下服务环境变量已由 Compose 配置；修改服务连接时应编辑对应 environment，而不是只添加一个未被 Compose 引用的 `.env` 字段：

| 服务 | 环境变量 | 默认连接或路径 |
| --- | --- | --- |
| Next.js | BACKEND_URL | http://backend:8080 |
| Java | DB_URL | jdbc:mysql://mysql:3306/studio?allowPublicKeyRetrieval=true&useSSL=false |
| Java | DB_USER | studio |
| Java | DB_PASSWORD | 与 Compose 的 DB_PASSWORD 相同 |
| Java | DATA_DIR | /data，挂载 files 卷 |
| Java | DEMO_ENABLED / COOKIE_SECURE | 由上述 Compose 配置传入 |

MySQL 首次使用空卷启动时按文件名顺序执行 `database/001-schema.sql`、`002-seed.sql` 和 `003-expanded-templates.sql`。种子显式使用 UTF-8，初始化十二个模板、三个分类，并覆盖需要 1 / 2 / 3 张图片的情况。表包括 `visitors`、`templates`、`assets`、`tasks`、`task_assets` 和 `task_steps`。数据库保存在 `database` 卷，文件保存在 `files` 卷。

已有数据卷不会重新执行初始化 SQL，也不会因修改 `.env` 自动变更数据库内已有账号密码。保留数据升级时需显式执行兼容变更；一次性验收可在确认允许清空后重建测试卷。

从六模板旧版升级且需要保留历史时，启动 MySQL 后执行以下增量脚本。脚本只插入缺少的六个模板，可重复执行，不删除任务、素材或访客：

```sh
docker compose up -d mysql
docker compose exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /docker-entrypoint-initdb.d/003-expanded-templates.sql'
```

`demo-assets/demo.png` 是随源码提供的可上传文件。模板封面和模拟结果由 Java Graphics2D 本地生成，自制算法不依赖外站资源、付费服务或内部素材。当前所有模板均允许 JPEG / PNG，单张最大 5 MiB；前端读取模板接口的数量、类型和大小要求，后端再次校验。

## 4. 主要接口与字段

以下接口均由 Java 提供，经 Next.js 同源转发。个人资源使用当前 Cookie 进行归属检查，不接受客户端指定 userId。JSON 字段统一使用 camelCase。

| 接口 | 请求与响应 |
| --- | --- |
| POST /api/session | 无请求体；创建或复用访客，返回 id、demoEnabled，首次建立身份时设置 Cookie |
| GET /api/me | 当前访客的 id、demoEnabled |
| GET /api/categories | 分类字符串数组 |
| GET /api/templates?category=旅行 | 模板数组；不传分类时返回全部模板 |
| GET /api/templates/{id} | id、title、category、description、coverUrl、requiredImageCount、acceptedMimeTypes、maxFileSizeBytes |
| GET /api/public/cover/{id} | 公共模板封面 PNG |
| POST /api/assets | multipart：templateId、file；返回 assetId、mimeType、sizeBytes、previewUrl |
| GET /api/assets/{id}/content | 当前访客拥有的上传文件内容 |
| POST /api/tasks | templateId、按上传位排列的 assetIds、可选 simulation；202 返回任务 ID 及当前任务快照 |
| GET /api/tasks/{id} | 任务快照及 steps 执行记录 |
| GET /api/me/tasks?limit=20&offset=0 | 个人历史数组，按创建时间与 ID 倒序；limit 为 1～100，offset 为非负整数 |
| GET /api/me/tasks/active | 当前访客的全部 QUEUED / RUNNING 任务，用于刷新恢复 |
| GET /api/tasks/{id}/result | 当前访客的成功结果 PNG |
| POST /api/dev/session/switch | 仅演示模式，接收 persona 为 A 或 B，设置对应访客 Cookie |
| GET /api/health | 数据库可查询时返回 status 为 ok |

上传响应示例：

```json
{
  "assetId": "服务端生成的素材ID",
  "mimeType": "image/png",
  "sizeBytes": 94150,
  "previewUrl": "/api/assets/服务端生成的素材ID/content"
}
```

创建任务示例：

```json
{
  "templateId": "portrait",
  "assetIds": ["上传返回的assetId"],
  "simulation": {
    "failStage": "GENERATE",
    "delaysMs": {"assetCheck": 1500, "generate": 8000, "finalize": 1500}
  }
}
```

任务快照包含 `id`、`templateId`、`title`、`status`、`currentStage`、`stateVersion`、`createdAt`，完成后包含 `finishedAt`；成功包含 `resultUrl`，失败包含 `failureStage`、`errorMessage`。时间返回为服务端日期时间，界面转换为浏览器本地时间。202 返回时任务可能已从排队进入处理，不能假定响应永远是 QUEUED。

详情查询额外返回 `steps` 数组。每步包含 `taskId`、`stage`、`sequenceNo`、`status`，开始或结束后有 `startedAt`、`finishedAt`，失败时有 `errorMessage`。尚未发生的时间和失败字段为空或缺省，不返回服务器文件路径和内部配置快照。

成功与失败任务的关键字段示例：

```json
{"id":"任务ID","status":"SUCCEEDED","currentStage":"FINALIZE","stateVersion":4,"resultUrl":"/api/tasks/任务ID/result"}
```

```json
{"id":"任务ID","status":"FAILED","currentStage":"GENERATE","failureStage":"GENERATE","errorMessage":"模拟生成未能完成：已触发演示失败，请新建任务"}
```

上面两段用于说明结果字段，完整响应还包含模板、创建时间等公共字段。

参数不合法返回 400，身份无效返回 401；读取不存在或无权访问的素材、任务、结果返回 404，创建任务时引用不存在或不属于当前访客的素材返回 400。文件超限返回 413，类型或解码失败（含截断的 PNG / JPEG）返回 415，代理无法连接 Java 时返回 503。损坏图片在写文件和创建素材记录前被拒绝；磁盘或数据库故障不伪装为图片类型错误。错误响应的 `message` 用于界面提示。只有查询成功且任务 `status=FAILED` 才是已知执行失败；网络错误或 5xx 属于查询异常。

## 5. 任务状态、阶段与后台执行

| 整体状态 | 含义 |
| --- | --- |
| QUEUED | 已创建记录，等待工作线程 |
| RUNNING | 正在执行某个阶段 |
| SUCCEEDED | 三步完成，结果可预览 |
| FAILED | 执行或调度失败，保存原因 |

正常阶段为 `WAITING → ASSET_CHECK → GENERATE → FINALIZE`，对应等待、素材检查、模拟生成、结果整理。每个步骤另有 PENDING / RUNNING / SUCCEEDED / FAILED 状态。某步失败后后续步骤保持 PENDING，表示没有执行；调度失败时失败阶段可以是 WAITING。

上传接口先校验类型与大小；受理接口校验模板、素材数量、归属和制作参数，在一个事务中创建任务、素材关联及三步记录，提交后交给 Java 有界线程池。默认一个工作线程、最多 64 个排队任务；多任务按队列执行。接口不等待延迟、生成或结果整理，浏览器关闭不会中断已受理任务。

每步开始与结束分别持久化，模拟等待期间不持有贯穿全流程的数据库事务。失败立即停止后续步骤；结果文件写好后才发布成功状态和结果地址。查询只读取 MySQL，不推进任务；终态更新受 SQL 条件保护。`stateVersion` 随任务状态更新递增，用于丢弃前端迟到的旧快照。

前端分别管理上传位、提交动作、后台任务和查询错误。受理后恢复提交按钮并显示任务卡；按 taskId 隔离各任务的阶段、错误和结果，仅轮询未结束任务，约 2 秒一次。任务结束后停止跟踪，没有待处理任务时不轮询。页面离开、身份切换时取消请求和定时器，使用身份代次、上传位序号及快照版本拦截迟到响应。

“重新加载”期间暂停跟踪，初始化结束后按新的代次重建轮询；即使身份和任务 ID 未变，也会继续自动查询。重新加载再次失败时保留原身份的任务信息，提供重试入口；查询异常仍不等于任务执行失败。

## 6. 成功、指定阶段失败、耗时与恢复演示

默认启用演示模式。浏览器可直接完成：打开“午后肖像” → 上传 demo.png → 开始制作 → 查看当前阶段 → 预览成功结果。上传位支持预览、替换和移除；数量不足、上传中、上传失败或提交请求中不能重复提交。

模拟控制不放在正式制作表单中，通过演示脚本或创建接口传入。以下命令使用访客 A；在浏览器右上角打开访客弹窗并切换为 A，可以看到脚本创建的同一批记录：

```sh
# 成功任务
docker compose run --rm demo --persona A

# 三种可重复的失败
docker compose run --rm demo --persona A --fail-stage ASSET_CHECK
docker compose run --rm demo --persona A --fail-stage GENERATE
docker compose run --rm demo --persona A --fail-stage FINALIZE

# 每一步等待 10 秒，便于观察
docker compose run --rm demo --persona A --delay 10000
```

`failStage` 允许 null、ASSET_CHECK、GENERATE、FINALIZE；null 表示不注入失败。`delaysMs` 按阶段配置毫秒数，每项为 0～60000 整数；省略时使用 1200 / 6000 / 1200 毫秒。模拟配置在受理时快照保存，不随机失败，也不影响之前已经创建的任务。关闭演示模式后拒绝客户端模拟参数，后台仍使用默认模拟生成器。

连续创建与刷新恢复的演示步骤：

1. 提交一个慢任务后，继续选择其他模板上传并提交，观察独立任务卡；或者在两个终端分别运行上述成功和指定失败命令，均使用 persona A。
2. 在处理期间刷新页面或重新打开同一浏览器身份的页面，Java 返回历史和全部 active 任务，前端恢复跟踪。
3. 任务结束后查看创建时间、成功预览或失败阶段与原因；已结束任务不再轮询。
4. 在浏览器暂时断网，查看保留的任务快照和“恢复查询”；恢复网络后继续读取服务端真实状态。不要将此操作等同于停止 Java 容器。

## 7. 用户身份与个人记录

使用匿名访客方案，不实现邮箱密码或第三方登录。Java 创建随机、不透明的 `studio` Cookie，设置 HttpOnly、SameSite=Lax 和 30 天有效期；数据库保存 SHA-384 令牌摘要（Base64 URL 编码为 64 字符）和访客 ID。每个素材、任务、历史及结果接口检查当前访客归属；越权读取返回 404，提交不属于当前访客的素材返回 400。

刷新和同一浏览器再次访问会复用有效 Cookie。使用无痕窗口可创建独立访客，验证个人记录隔离。演示模式额外提供固定访客 A / B，便于重复验收；切换时清理旧请求和旧用户界面，A 的记录不会显示到 B。演示令牌是本地固定值，不作为正式认证凭据。

清除 Cookie 后无法找回随机匿名身份，不支持跨设备登录。关闭 `DEMO_ENABLED` 后不显示演示切换入口，开发切换接口返回 404。HTTPS 部署时设置 `COOKIE_SECURE=true`；本地 HTTP 保持 false。

从旧版令牌摘要方案升级时，已有随机匿名 Cookie 无法与新摘要匹配，将建立新访客；旧访客和历史仍在数据库中，但新访客不能读取它们。需要保持旧匿名访问时应先保留旧版应用、数据库和文件备份，不直接升级该环境。固定演示访客 A/B 再次切换时更新其摘要并复用原访客 ID，因此可继续查看原演示记录。数据库字段长度无需变更；本项目不生成压缩包摘要校验文件。

## 8. CI 与验证

`.github/workflows/ci.yml` 在 push 或 pull_request 时执行容器构建，构建中包含前端 lint、TypeScript 类型检查、测试和生产构建，以及 Java 测试和打包；随后启动服务并运行真实 API 冒烟，结束时清理 CI 临时环境。不要求自动部署。

测试覆盖后端三步顺序、每阶段失败及后续中断、异步受理、任务隔离、真实图片与截断 PNG / JPEG 校验、磁盘错误区分及参数检查；前端覆盖多任务隔离、查询异常不等于执行失败、同身份重新加载后继续跟踪、重复恢复、终态停止轮询、请求取消、身份切换和旧上传迟到。

本地容器构建、测试和运行结果见 `docs/acceptance-report.md` 及其证据文件。远程运行状态以 [GitHub Actions](https://github.com/onekeaf/material-studio/actions) 中对应提交的 checks 工作流为准，本地和远程验证记录分别保留。

逐项测试覆盖、源码审阅和浏览器检查的对应关系及未验证边界见 `docs/test-matrix.md`。当前自动测试包含前端 37 项、Java 26 项，另有使用真实 MySQL 的接口验收；数量不代表覆盖了所有可能输入。

## 9. 完成情况、限制、实际投入与取舍

| 范围 | 完成情况 |
| --- | --- |
| 模板与制作页（R-02、R-03、R-11） | 分类、详情、动态上传位、校验、状态反馈、桌面与小屏布局、键盘标签和结果预览已实现 |
| 异步与状态（R-04～R-08） | 快速受理、三步执行、失败中断、可调模拟、多任务及查询生命周期已实现 |
| 个人记录（R-09、R-10） | 匿名身份、归属隔离、历史分页、刷新恢复和未结束任务跟踪已实现 |
| 工程交付（R-01、R-12～R-14） | 指定技术栈、持久化、源码 / 依赖 / SQL、测试、CI 配置、README 与 Compose 已提供 |

未实现和已知限制：

- 不支持运行中任务在 Java 重启后自动续跑。原 QUEUED / RUNNING 可能停留最后记录；已结束历史和文件会保留。浏览器刷新恢复不等于服务重启恢复。
- 使用单实例内存队列和本地文件卷，不支持多实例调度或共享存储；不实现受理幂等、断点续跑和自动重试。提交响应不确定时先查历史，不自动重发 POST。
- 未绑定的上传文件不自动清理，未设置存储配额；历史使用 limit / offset，大量数据下可改为游标分页。
- 不实现真实 AI、支付、会员和复杂模板管理。模拟结果是预置风格示意，不体现用户上传照片的加工。
- 跨浏览器兼容矩阵、正式无障碍审计、压力测试和灾备恢复演练未验证；远程 CI 状态见上述 Actions 记录。

两天工作量分配（估算，每天 8 小时）：

| 内容 | 时间 |
| --- | --- |
| 需求分析、接口与数据结构设计 | 第一天，1 小时 |
| Java 接口、身份隔离、数据库与文件存储 | 第一天，3 小时 |
| 三步骤异步处理、失败控制与状态查询 | 第一天，2 小时 |
| 模板列表、详情与上传交互 | 第一天，2 小时 |
| 多任务状态、历史记录、刷新与异常恢复 | 第二天，2 小时 |
| 后端与前端异常测试、问题修复 | 第二天，3 小时 |
| Docker、MySQL 与远程 CI 验证 | 第二天，2 小时 |
| README、验收材料与源码包整理 | 第二天，1 小时 |

可确认的 AI 辅助执行记录累计约 2 小时，详见 `docs/worklog.md`；人工总工时未独立统计，上表不作为已完成的实际工时。

取舍：使用单线程队列、预置图、本地数据卷、匿名身份和轮询，将时间投入异步状态、失败边界和可复现验证。

## 10. 基本部署与回滚

部署时在具备 Docker 的环境准备源码和配置，通过 `docker compose up --build -d --wait` 启动，再执行 `smoke` 验证。已有环境更新前应暂停新任务并等待当前任务结束，记录旧应用镜像，备份 MySQL 和 files 卷；数据库与文件应在同一停止写入窗口备份，保证结果路径和文件一致。

更新保留命名卷，使用兼容的数据库变更；现有卷不会自动应用新的初始化 SQL。健康检查通过后，验证一个成功任务、一个失败任务及历史读取。日常停机使用 `docker compose down`，不删除卷。

需要回滚时，停止新应用，恢复上一版应用镜像和配置，以 `docker compose up -d --no-build --force-recreate backend frontend` 重新创建应用，再做健康和业务检查。若 schema 或身份摘要方案不兼容，必须同时恢复对应数据库及文件备份，不能只回滚代码。更详细的步骤和边界见 `docs/deployment-and-rollback.md`；未声称执行过生产部署或灾备演练。

## 11. AI 使用与工作流

使用 Codex 辅助开发。先把需求拆为模板 / 素材、访客归属、任务受理、三步执行、前端状态和交付验证，并提供状态机、接口字段、数据结构与关键不变量作为上下文；按模块生成或修改代码，再在 Docker 中执行编译、测试和真实接口验收，根据失败输出修正实现。

具体例子：单任务的“恢复查询”最初只刷新历史第一页，较早任务可能无法直接恢复。补充组件测试后，修改为按 taskId 查询并保留原任务快照，再验证查询成功后终态任务停止轮询；同时用延迟 Promise 检查旧用户和旧上传响应不会覆盖当前数据。AI 参与了拆解、实现、测试与修正，没有调用真实 AI 服务生成制作结果。

后续审查又发现“身份和任务 ID 均未变时重新加载导致订阅停止”的遗漏：先保存失败复现，再增加响应式初始化代次，让恢复完成后重建轮询，并覆盖继续处理中、到达成功或失败以及再次恢复的场景。损坏图片也通过真实截断字节复现后补充接口测试和 415 映射。验证范围以当次日志为准，不把既有绿色 CI 当作未覆盖场景已经通过。

## 12. 来源与复用范围

| 来源 | 主要使用范围 |
| --- | --- |
| Next.js、React、TypeScript 官方包 | 路由、渲染、类型与请求代理；版本见 npm 锁文件 |
| Spring Boot、MyBatis、MySQL 及官方 Maven 依赖 | Web、依赖注入、数据库映射与存储；版本见 pom / BOM |
| JUnit、Mockito、Vitest、Testing Library、jsdom | 单元与组件测试基础设施 |
| Docker 官方 Node、Maven、Temurin、MySQL、Python 镜像 | 容器构建、运行和验收 |
| 题目参考截图 | 页面主要布局；没有复用其品牌、照片、积分或会员业务 |
| 本项目自制素材与页面文件 | Graphics2D 封面 / 结果 / 示例 PNG，以及页面、业务代码和 SQL；未整体复用已有业务项目 |

格式化曾使用 Prettier 和 Google Java Format，不属于应用运行依赖。补充来源记录见 `THIRD_PARTY_NOTICES.md`。
