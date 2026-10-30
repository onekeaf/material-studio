# 本地容器验收记录

## 2026-10-30 README 与身份方案一致性

前端容器 lint、类型、37 项测试和构建通过；当前后端源码挂载到已有依赖缓存的构建容器后执行离线 Maven verify，26 项测试和打包通过。默认后端构建首次下载依赖时遇到 TLS 握手失败，因此本地没有将默认联网构建标为通过；默认构建流程的结果看该提交的远程 CI。

使用上述新 JAR 和已有 Java 21 运行镜像，在独立 Compose 项目和全新 MySQL / 文件卷上运行完整 smoke 通过。额外将 A 演示访客设为旧摘要格式并写入历史，再切换 A，验证新摘要生效且旧历史仍可读；示例 PNG 在 smoke 前后逐字节一致。证据为 `evidence/readme-final-frontend.log`、`readme-final-backend.log`、`readme-final-smoke.log`、`readme-final-migration.log` 和 `readme-final-sample.log`。随机匿名身份升级的限制及回滚要求已在 README 说明。未执行生产部署或恢复演练。

## 2026-10-29 修复及扩展测试

- `frontend-check`：lint、类型检查、37 个测试和生产构建通过，见 `evidence/fix-expanded-frontend.log`。
- `backend-check`：25 个 Java 测试通过，Maven verify 和打包通过，见 `evidence/fix-expanded-backend.log`。
- 新建隔离 Compose 项目、空 MySQL 和文件卷启动应用后运行扩展 smoke：12 张本地封面、各阶段失败中断、无需查询完成、多任务、三种图片数、损坏/超限文件、身份隔离、分页和参数边界通过，见 `evidence/fix-expanded-smoke.log` 和 `evidence/smoke.json`。
- 同身份重新加载、任务仍运行后完成、再次恢复、实际步骤异常、队列饱和、空列表与请求失败区分都有回归测试。桌面、390px 与真实损坏图片反馈的本轮画面见 `evidence/fix-*.jpg`。
- R-01～R-14 与证据、人工检查和未验证边界的对应表见 `test-matrix.md`。跨浏览器、压力、正式无障碍和灾备恢复未执行。

## 2026-10-28 历史记录

日期：2026-10-28；执行环境：Windows 上的 Docker Desktop Linux 容器。以下是当时版本的历史验证；远程 CI 状态以 https://github.com/onekeaf/material-studio/actions 中对应提交的运行结果为准。公网部署和灾备恢复没有执行。

## 验证入口与结果

| 入口 | 结果 | 证据 |
| --- | --- | --- |
| docker compose build | 前端 lint / 类型 / 测试 / 构建，Java 测试 / 打包通过 | evidence/optimization-build.log |
| docker compose run --build --rm frontend-check | 18 个前端测试通过，lint、类型检查和 Next 构建通过 | evidence/optimization-frontend-check.log |
| docker compose run --build --rm backend-check | 9 个 Java 测试通过，Maven verify 成功 | evidence/optimization-backend-check.log |
| docker compose run --rm smoke | 真实 Next / Java / MySQL / 文件闭环通过 | evidence/optimization-smoke.log、smoke.json |
| 关闭演示模式的独立环境 | 能力标志为 false；切换返回 404，模拟参数返回 400；默认任务成功、结果可读 | evidence/optimization-config.log |

## 核心行为

- 后端测试覆盖三步顺序、三个指定阶段失败后不调用后续步骤、真实异常中断、异步提交和多任务隔离。
- 上传测试使用实际 JPEG / PNG 字节，验证落盘及不信任浏览器 MIME；无效参数测试验证创建前拒绝。
- 前端测试覆盖多个任务互不串扰、查询失败保留状态、恢复请求按 taskId、终态停止轮询、旧上传迟到、身份切换、离开页面取消请求和按模板要求校验。
- 真实接口验证中文种子、模板字段、图片上传 / 结果、数量 / 类型 / 超限、个人素材 / 任务 / 结果隔离、无 GET 时后台完成、三步持久化、版本稳定和多任务。
- 三个失败点各重复两次，验证失败阶段正确、后续步骤保持 PENDING。步骤对外字段为 camelCase，未开始的时间为空或缺省。
- 并发建立同一演示身份可复用记录；默认演示模式与关闭演示模式分别验证。

## 浏览器与历史证据

此前已在浏览器检查模板分类、详情、上传、受理后按钮恢复、刷新后读取结果、图片预览、Escape / 焦点恢复和身份切换；390px 下实际完成上传与提交。相关画面见 evidence/catalog.png、workspace.png 和 mobile.png。

此前独立检查了 Java 重启后终态历史及结果文件仍可读取，见 evidence/persistence-result.json。该结果不能证明运行中任务重启续跑；此能力不实现。

早期 build.log、review-* 等文件保留为历史证据，其中的测试数量属于当时版本。当前数量与结果以上面的 optimization-* 为准；旧截图不用于证明本次新加的配置或请求清理分支。

## 未验证与限制

未执行跨浏览器 / 操作系统矩阵、正式无障碍审计、压力测试、生产发布和灾备恢复。没有真实 AI、受理幂等、自动重试或运行中任务重启恢复。CI 运行状态由远程 Actions 记录提供，本地证据与远程结果分别保留。

所有项目构建、安装、测试和执行均在容器中进行，没有要求评估者在宿主运行项目脚本。

## 源码包复验

更新后的源码包解压到新目录，使用独立 Compose 项目 studio-readme-release 和全新数据库 / 文件卷执行 up --build，再运行 smoke，结果通过。见 evidence/readme-release-smoke.log。该临时项目已清理，默认应用保持运行。

