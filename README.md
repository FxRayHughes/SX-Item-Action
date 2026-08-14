# SX-Item-Action

专用于 SX-Item 4.x 的物品动作附属。动作直接写在 SX-Item 的物品节点中，同时支持 TabooLib Kether 与 Fluxon 1.7.2，也支持从本插件 `scripts/` 目录导入可复用脚本。

## 设计边界

- 只接受带 `SX-Item.ItemKey` 身份标记、且能由 SX-Item `ItemManager` 找到生成器的物品。
- 不按名称、Lore 或材质猜测物品身份，因此不会误兼容其他物品插件。
- Bukkit 事件统一转换成 `ActionContext`，Kether 与 Fluxon 使用相同变量名和同一个 `vars` 映射。
- 配置变量先通过 SX-Item `ExpressionHandler` 解析，再注入脚本，可使用 SX-Item 表达式、Random 与 PlaceholderAPI。

## SX-Item 物品配置

将 `Action` 写在现有物品节点内：

```yaml
ExampleSword:
  ID: DIAMOND_SWORD
  Name: '&b示例之剑'
  Random:
    damage:
      - '10'
      - '20'
  Action:
    on-right-click:
      engine: kether
      import: example
      cancel: true
      variables:
        message: '&a你使用了 %player_name% 的 <s:damage> 点力量'

    on-attack:
      engine: fluxon
      script: |-
        &context.cancelEvent()
        &player.sendMessage("item=" + &itemId + ", damage=" + &damage)
      variables:
        damage: '<i:10_20>'

    on-timer:
      - engine: fluxon
        import: example
        variables:
          message: '&7定时效果来自 Fluxon'
      - engine: kether
        script:
          - 'tell color &message'
```

支持入口：`on-left-click`、`on-right-click`、`on-right-click-entity`、`on-attack`、`on-block-break`、`on-block-place`、`on-item-break`、`on-consume`、`on-drop`、`on-pickup`、`on-swap-to-mainhand`、`on-swap-to-offhand`、`on-timer`。同时兼容 Zaphkiel 蛇形名和 MythicItemStyrke 驼峰名。

## 预制脚本

- Kether：`plugins/SXItemAction/scripts/kether/<name>.ks`
- Fluxon：`plugins/SXItemAction/scripts/fluxon/<name>.fs`
- 配置使用 `import: <name>`，扩展名可省略。

两套引擎都能读取：`player`、`item`、`event`、`trigger`、`itemId`、`vars`、`context`，以及 `variables` 下的每个同名顶层变量。动作列表按顺序执行，并共享 `vars`，因此前一个脚本写入的值可被后一个脚本读取。

## 取消事件

声明式取消使用 `cancel: true`。如需根据脚本条件动态决定：

```kether
if check &shouldCancel then {
  sx-event cancel
}
```

```fluxon
if &shouldCancel then &context.cancelEvent()
```

Kether 还支持 `sx-event uncancel`、`sx-event cancelled`；Fluxon 对应 `&context.uncancelEvent()`、`&context.isEventCancelled()`。这些方法对不可取消的 `on-timer` 返回 `false`，不会抛出异常。

事件取消必须在脚本的第一次 `wait`、异步调用或其他挂起点之前完成。Bukkit 事件回调一旦返回，之后再修改取消状态不保证有效。动作列表中只有第一个脚本能可靠执行动态取消；如果后续脚本也需要决定是否取消，请把判断合并到第一个脚本，或使用同步的 `cancel: true`。

Fluxon 脚本属于服务器管理员可信代码，能够通过 Bukkit 对象访问服务器运行时；不要加载来源不明的脚本。

## 管理命令

- `/sxia reload`：重载 `scripts/` 目录。
- `/sxia status`：查看 SX-Item 状态、脚本数量、执行失败及取消统计。
- `/sxia validate`：检查未知目录、扩展名错误、空脚本和重复脚本键。
- `/sxia selftest`：玩家执行 Kether、Fluxon、顺序动作链与事件取消运行时自检。

权限节点为 `sxitemaction.admin`。

构建后部署 `build/libs/SX-Item-Action-<版本>.jar`。Fluxon 运行时由 TabooLib 在首次启动时从官方仓库下载，并重定向到本插件私有包；后续启动直接复用隔离后的本地依赖缓存，不会与其他插件的 Fluxon 版本冲突。
