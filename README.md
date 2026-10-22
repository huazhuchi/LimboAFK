# LimboAFK

基于 [LimboAPI](https://github.com/Elytrium/LimboAPI) 的 Velocity 代理层 AFK 分流插件。

玩家长时间没有有效操作后，会被送入 LimboAPI 创建的虚拟虚空；在虚空中移动、转头、聊天或输入指令，即可回到进入前的服务器。

> 本项目仅用于自用私服。插件不包含自动点击、自动挂机机器人等逻辑。

## 功能

- AFK 超时自动分流到 Limbo 虚空，支持提前警告
- `/afk` 手动开启或取消 AFK 标记
- `/limbo reload` 热重载配置并重建 Limbo
- `limboafk.bypass` 权限绕过自动检测（手动 `/afk` 仍然有效）
- 虚空内自定义标题、ActionBar、聊天提示，支持 `&` 与 `§` 颜色码
- 可配置出生坐标、维度、空背包、伤害/饥饿处理、坠落冻结
- 进入前缓存原服务器，退出时优先返回原服；原服不可用时回退到 `attempt-connection-order`
- 控制台记录 AFK 预警、进入/离开 Limbo 等事件
- 纯 Velocity 代理层实现，不需要任何后端 Spigot/Paper 插件

## 运行要求

| 组件 | 要求 |
|---|---|
| Java | 17+ |
| Velocity | 3.3.0+ |
| LimboAPI | 与 Velocity 版本匹配 |
插件只使用 LimboAPI 的稳定 API（`LimboFactory`、`Limbo`、`LimboPlayer`、`LimboSessionHandler`），更换 LimboAPI 小版本通常不需要重新编译。若 LimboAPI 启动时提示 `Please update Velocity`，说明 LimboAPI 与当前代理版本不匹配，请调整两者的版本组合。

## 工作原理

```text
聊天 / 指令 / 切服
        │
        ▼
ActivityListener ──touch()──> AfkManager ──tick()──> 预警 ──超时──> LimboService
                                                                        │
                                             VirtualWorld(THE_END + 空区块)
                                                                        │
                                                                        ▼
                                                              AfkLimboHandler
                                             ├─ onSpawn：标题 / ActionBar / 聊天 / 模式 / 清背包
                                             ├─ onMove / onRotate / onChat
                                             └─ onDisconnect
                                                                        │
                                                                        ▼
                                                  LimboPlayer.disconnect(原服务器)
```

- `AfkManager` UUID 维护玩家状态，调度器按 `check-interval` 扫描。
- `afk-timeout - warning-before` 时发送一次警告，`afk-timeout` 时送入 Limbo。
- 发送到 Limbo 前记录 `player.getCurrentServer()`，因为 LimboAPI 送入后会清空该连接。
- 全局复用一个 `Limbo` 实例，`/limbo reload` 时创建新实例并延迟释放旧实例。
- Limbo 内水平位移超过 0.5 格（开启冻结坠落时也包含纵向）、转头超过 2 度、任意聊天或指令都会触发返回。
- 如果玩家在 Limbo 内掉线，清理状态并记录日志。

### 代理层检测边界

这是本插件最重要的限制：

**Velocity 只能观测聊天、指令、切换服务器。** 玩家在后端服务器内的移动、转头、交互不会产生代理事件，代理只是原样转发数据包。因此大厅/游戏内的自动 AFK 检测按“无聊天、无指令、未切服”计时，无法判断玩家是否在移动。

进入 Limbo 后的移动、转头、聊天、指令检测是完整的，因为此时玩家连接由 LimboAPI 的会话处理器接管。

若必须在大厅内精确检测移动，需要后端插件上报或做协议层数据包拦截，这超出了“纯代理层、只用 LimboAPI”的范围。实际使用时建议：

- 将 `afk-timeout` 设得足够长（例如 300 秒以上）
- 依赖 `/afk` 手动标记
- 给不确定的玩家授予 `limboafk.bypass`

## 构建

项目使用 Maven，Java 17 目标。

```bash
mvn clean package
```
## 安装

1. 将对应版本的 `limboapi-*.jar` 放入 Velocity 的 `plugins/` 目录。
2. 将 `LimboAFK-1.0.0.jar` 放入 `plugins/` 目录。
3. 启动代理，首次启动会生成 `plugins/limboafk/config.yml`。
4. 按需修改配置，执行 `/limbo reload` 或重启代理。
5. 使用 LuckPerms 等权限插件授予权限。

Velocity 本身没有内置权限默认值；没有权限插件时，`/afk` 可能因权限不足不可用。控制台默认拥有全部权限。

## 命令与权限

| 命令 | 说明 | 权限 |
|---|---|---|
| `/afk` | 开启/取消手动 AFK 标记 | `limboafk.afk` |
| `/limbo reload` | 重载配置并重建 Limbo | `limboafk.reload` |

| 权限 | 说明 |
|---|---|
| `limboafk.afk` | 使用 `/afk` |
| `limboafk.reload` | 使用 `/limbo reload` |
| `limboafk.bypass` | 不会被自动送入 Limbo，手动 `/afk` 仍然有效 |

LuckPerms ：

```text
/lp group default permission set limboafk.afk true
/lp group admin permission set limboafk.reload true
/lp user <玩家> permission set limboafk.bypass true
```

## 配置

完整模板见 [`src/main/resources/config.yml`](src/main/resources/config.yml)，首次启动会释放到 `plugins/limboafk/config.yml`。

### 通用

| 键 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 插件总开关 |
| `auto-afk` | `true` | 是否开启自动 AFK 检测；关闭后仅 `/afk` 手动触发倒计时 |
| `afk-timeout` | `300` | 无代理层可观测动作多少秒后送入 Limbo |
| `warning-before` | `30` | 提前多少秒警告；`0` 表示不警告 |
| `check-interval` | `5` | 扫描周期（秒） |

### 指令开关

| 键 | 默认值 | 说明 |
|---|---|---|
| `commands.afk-enabled` | `true` | 是否启用 `/afk` |
| `commands.limbo-enabled` | `true` | 是否启用 `/limbo` |

### 权限节点

| 键 | 默认值 |
|---|---|
| `permissions.afk` | `limboafk.afk` |
| `permissions.reload` | `limboafk.reload` |
| `permissions.bypass` | `limboafk.bypass` |

### 文案

所有文案支持 `&` 与 `§` 颜色码，支持占位符 `%seconds%`、`%player%`、`%server%`。

| 键 | 说明 |
|---|---|
| `messages.prefix` | 聊天消息前缀 |
| `messages.afk-marked` | 手动标记 AFK 提示 |
| `messages.afk-cancelled` | 取消 AFK 提示 |
| `messages.afk-warning` | 进入 Limbo 前的警告 |
| `messages.limbo-title` | Limbo 标题主文本 |
| `messages.limbo-subtitle` | Limbo 标题副文本 |
| `messages.limbo-action-bar` | Limbo ActionBar |
| `messages.limbo-chat` | 进入 Limbo 后的聊天提示，支持 `\n` 换行 |
| `messages.limbo-returned` | 返回服务器提示 |
| `messages.no-server` | 原服不可用且无后备服时的提示 |
| `messages.limbo-failed` | 送入 Limbo 失败提示 |
| `messages.reloaded` | 重载成功提示 |
| `messages.reload-failed` | 重载失败提示 |
| `messages.no-permission` | 无权限提示 |
| `messages.command-disabled` | 指令被配置禁用时的提示 |
| `messages.players-only` | 仅玩家可执行时的提示 |
| `messages.usage` | `/limbo` 用法提示 |

### Limbo 世界

| 键 | 默认值 | 说明 |
|---|---|---|
| `limbo.dimension` | `THE_END` | 虚拟维度：`THE_END`（黑色虚空）、`NETHER`、`OVERWORLD` |
| `limbo.spawn-x` | `0.0` | 出生点 X |
| `limbo.spawn-y` | `100.0` | 出生点 Y |
| `limbo.spawn-z` | `0.0` | 出生点 Z |
| `limbo.yaw` | `0.0` | 出生朝向 |
| `limbo.pitch` | `0.0` | 出生俯仰角 |
| `limbo.empty-inventory` | `true` | 进入时清空客户端背包槽位（0-45） |
| `limbo.disable-damage` | `true` | 关闭伤害：使用冒险模式，虚空内无伤害源 |
| `limbo.disable-hunger` | `true` | 关闭饥饿：使用旁观模式，客户端不显示生命/饥饿条 |
| `limbo.freeze-falling` | `true` | 冻结坠落，让玩家悬浮；建议保持开启 |

游戏模式优先级：`disable-hunger: true` → 旁观模式；否则 `disable-damage: true` → 冒险模式；否则生存模式。

想保留第一人称手臂与生命/饥饿条，将 `disable-hunger` 设为 `false`。

> 清空背包只作用于 Limbo 客户端。返回后端服务器时，后端会重新下发它保存的背包数据，不会丢失物品。

## 日志

控制台会输出以下事件：

- AFK 预警（包含剩余秒数）
- 玩家因 AFK 进入 Limbo（包含原服务器）
- 玩家离开 Limbo（包含触发原因与目标服务器）
- 手动标记 AFK
- 在 Limbo 中掉线
- 配置重载与 Limbo 重建

日志文件为 UTF-8。若 Windows 终端出现中文乱码，通常是终端代码页问题，可改用 UTF-8 终端或查看 `logs/latest.log`。

## 常见问题

### 插件没有加载

确认：

- LimboAPI 已放入 `plugins/`
- `velocity-plugin.json` 中存在 `"dependencies":[{"id":"limboapi","optional":false}]`
- 使用了 Maven 构建，而不是仅用 `javac` 手工编译

### LimboAPI 提示 `Please update Velocity`

LimboAPI 与 Velocity 版本不匹配。例如 LimboAPI 1.1.18 需要支持 1.20.4 的 Velocity 3.3.0 构建（build 310+）。请调整版本组合。

### Maven 报 `adventure-nbt:4.15.0-SNAPSHOT` 找不到

LimboAPI 1.1.18 的 POM 带有这个已失效的 SNAPSHOT 依赖。本项目 `pom.xml` 已排除 LimboAPI 的传递依赖并显式添加 `adventure-nbt:4.17.0`，不要删除该排除配置。

### `/afk` 提示没有权限

Velocity 没有内置权限默认值。使用 LuckPerms 授予 `limboafk.afk`，或检查配置中的 `permissions.afk` 是否被改成了其他节点。

### 玩家明明在动，还是被送入 Limbo

这是代理层限制：Velocity 看不到后端服务器内的移动/转头/交互。请增大 `afk-timeout`、让玩家使用 `/afk`，或给玩家 `limboafk.bypass`。进入 Limbo 后的移动检测不受此限制。

### 玩家在 Limbo 里回不来

- 确认 `limbo.freeze-falling: true`；关闭后玩家会持续下落
- 移动超过 0.5 格、转头超过 2 度、发送任意聊天或指令都会触发返回
- 原服务器不可用时会回退到 `velocity.toml` 的 `attempt-connection-order`；若都没有可用服务器，玩家会被断开

### 玩家在好友列表里显示离线

进入 Limbo 时 LimboAPI 会关闭玩家的后端连接，后端会把该玩家从其他玩家的 Tab/PlayerInfo 中移除，基于后端连接状态的好友插件会判定为离线。

如果好友系统完全运行在代理层，它仍然能看到玩家在线，需要该插件自行处理。

### 控制台中文乱码

日志文件本身是 UTF-8。Windows 旧终端可能按 GBK 显示，换用 Windows Terminal 或查看 `logs/latest.log` 即可。

## 项目结构

```text
LimboAFK/
├── pom.xml
├── README.md
└── src/main/
    ├── java/cn/basiker/limboafk/
    │   ├── LimboAFKPlugin.java          # 插件入口、初始化、指令注册
    │   ├── afk/
    │   │   ├── AfkManager.java          # AFK 状态机、计时、送入/退出 Limbo
    │   │   ├── AfkSession.java          # 单玩家状态
    │   │   └── AfkLimboHandler.java     # Limbo 会话回调
    │   ├── limbo/
    │   │   └── LimboService.java        # 创建和管理 Limbo 实例
    │   ├── listener/
    │   │   ├── ActivityListener.java    # 聊天/指令/切服活动
    │   │   └── ConnectionListener.java  # 登录/断开
    │   ├── command/
    │   │   ├── AfkCommand.java
    │   │   └── LimboCommand.java
    │   ├── config/
    │   │   ├── ConfigManager.java       # 配置加载与重载
    │   │   └── Settings.java            # 配置快照
    │   └── util/
    │       └── Text.java                # 颜色码转换
    └── resources/
        └── config.yml
```

## 免责声明

本项目仅供自用私服。
