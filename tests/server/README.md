# 服务端回归测试

这是专用测试服工具，不能用于生产服。探针使用真实 SX-Item 生成器、NBT 包装接口及 Bukkit 事件链；客户端使用 Mineflayer 真实连接、执行管理命令和挖掘方块。通过探针分发的边界事件与真实挖掘测试分别记录，不能把探针事件当作全部玩家操作已覆盖。

运行前先构建插件，再用 Java 8 目标编译 `SXIALifecycleProbe.java`，编译依赖为 Spigot API 1.12.2 和测试服 SX-Item JAR；将编译结果与本目录 `plugin.yml` 打入 `build/SXIA-Lifecycle-Probe.jar`。

`run.py` 按本次指定的本机环境配置路径：

- 测试根目录：`E:/Minecraft-Server/incisionTest`。
- 服务端：`run-26.2-paper`、`run-1.21.11-paper`，端口 25565，需已有 EULA 同意及离线登录配置。
- Mineflayer 依赖：测试根目录 `devdump/node_modules`；26.2 通过已安装的 ViaVersion/ViaBackwards 使用 1.21.11 客户端。
- 专用账号：`SXIALifeTest`。脚本会清空该账号的物品栏、末影箱和光标，不得复用真实玩家。
- 输出：测试根目录 `sxia-lifecycle-results/<版本>-<时间>`，包含新日志、逐步断言、JSON 结果和原文件备份。

```powershell
python tests/server/run.py 26.2
python tests/server/run.py 1.21.11
```

两个服务器共用端口，必须顺序运行。驱动拒绝占用中的端口，仅关闭自己启动的进程；每轮临时部署 JAR、示例、脚本和探针，并移出会自动运行其他用例的 Incision-Test，结束时还原这些文件及 OP 列表。它会保存专用账号的测试数据并重启一次，验证离线到期与 NBT 持久化。探针正常卸载时恢复实际挖掘用的单个方块。强制中断脚本或主机掉电不保证清理执行，应使用结果目录的备份恢复。

验证范围以本次输出的 PASS 断言为准。其他交互场景继续参见 [完整验收清单](../lifecycle-manual.md)。故意损坏状态、非法日期、时长溢出用例会产生预期警告，不应据此忽略任何额外异常。
