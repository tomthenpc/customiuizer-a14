# LSPosed 服务 binder 的投递路径与它的失败模式

> 结论摘要：binder 是守护进程**推**给应用的，应用侧**没有**索取或重试的能力。
> 连续快速重启后守护进程可能永久停推。设置应用能做的只有「不丢数据、不误判、说清楚」。

本文记录 `r14.13.7` 定位「暂未连接到 LSPosed 服务」时查到的机制与证据，供以后再遇到同类
现象时直接复用，不必重新反编译一遍。

## 投递路径

`libxposed-service` 102.0.0 的实际形状（反编译 `classes.jar` 得到，不是文档推测）：

1. 模块应用的 manifest 里由 AAR 合并进一个 ContentProvider：

   ```xml
   <provider
       android:name="io.github.libxposed.service.XposedProvider"
       android:authorities="${applicationId}.XposedService"
       android:exported="true" />
   ```

2. LSPosed/Vector 守护进程调用该 provider 的 `call("SendBinder", ...)`，把 binder 放在
   Bundle 的 `binder` 键里。

3. `XposedProvider.call()` 收到后转交
   `XposedServiceHelper.onBinderReceived(IBinder)`。

4. `onBinderReceived` 若此时还没有 listener，就把 service 放进静态 `mCache`；
   有 listener 就 `linkToDeath` 并直接回调 `onServiceBind`。

5. `XposedServiceHelper.registerListener(listener)` 只做两件事：把 listener 存进静态字段，
   然后排空 `mCache` 并对每个缓存项回调 `onServiceBind`。

**关键点：第 5 步不发起任何请求。** 整个类里没有任何指向守护进程的调用。第 2 步的推送是
binder 进入应用进程的唯一路径。

## 由此得出的三条硬约束

- **重复调用 `registerListener()` 不会带来新的绑定机会**，它只会重放已缓存的结果。
- **加长超时不会提高成功率**：等待的是一个可能永远不会到来的推送。
- **轮询没有可轮询的对象**：应用侧不存在「查询当前是否有 service」的接口。

## 观测到的失败模式

实机日志（连续快速切换语言，设置应用被反复 `killProcess` 并重启）：

| 时刻 | 应用进程 | 应用侧 `GET_BINDER` | 守护进程 `Sent module binder` |
| --- | --- | --- | --- |
| 11:19:17.645 | start 15475 | 17.675 | **17.747**（102 ms） |
| 11:19:22.909 | start 15945 | 22.937 | **22.989**（80 ms） |
| 11:19:29.233 | start 16379 | 29.266 | **29.320**（87 ms） |
| 11:19:34.182 | start 16523 | 34.213 | **34.275**（93 ms） |
| 11:19:38.480 | start 16617 | 38.512 | **（无）** |
| …至 11:26:06 | 又 15 次启动 | 每次都有 | **一次都没有** |

正常情况下绑定在 **80–102 ms** 内完成，所以 3500 ms 的超时从来不是瓶颈。第 5 次重启之后，
应用每次启动仍然照常向 system_server 的 `VectorZygiskBridge` 请求（说明 zygisk 注入正常、
模块本身是激活的），但守护进程侧再也没有推送，并且**没有任何错误行**。

同一份日志里其它模块、其它 uid 的加载完全正常，所以不是守护进程整体挂掉。

## 排查用命令

```bash
# 守护进程是否还在推送本模块
grep -n "Sent module binder to tv.withaibuild.customiuizer.r14" log/verbose_*.log

# 应用侧是否还在请求（uid 需按实机替换）
grep -n "GET_BINDER, callerUid=10372" log/verbose_*.log

# 应用自己的状态迁移（tag 会被 LSPosed 日志收集器抓取）
grep -n "\[Pengeek\]\[XposedService\]" log/verbose_*.log
```

第三条会看到 `service bound …ms after registration (generation N)`、
`no bind within 3500ms`、`mirrored N setting(s)`、`settings change not mirrored`。

## 「模块未激活」与「设置应用未绑定」无法在应用内区分

- `DISCONNECTED` 只来自 `onServiceDied` 或注册抛异常；
- `TIMED_OUT` 只表示没收到推送，既可能是模块没启用，也可能是守护进程没推；
- 模块不会被加载进自己的应用进程，所以应用进程里没有独立的「模块已激活」信号。

因此面向用户的文案必须停在「暂未连接到 LSPosed 服务」，不能断言模块未激活 —— 一个绑定很慢
但最终成功的场景会让这种断言直接变成假话。

## 应用侧的应对（`r14.13.7`）

不再试图恢复绑定（做不到），而是让这个状态无害且可见：

- `PrefsMirror` / `PrefsMirrorState`：绑定建立时全量对账，未下发的改动有明确状态并在
  对话框中告知；每次绑定一个代次，旧代次的 pass、延迟重试和结果回调全部失效。
- 快速重启改用有序广播直接尝试，不再拿设置应用的绑定状态去猜 SystemUI 里有没有模块。

## 上游

守护进程停止推送属于 LSPosed/Vector 侧问题，应用无法修复，宜向上游报告。复现要点：
让模块自身的应用进程在十几秒内被反复杀死并重启四次以上。
