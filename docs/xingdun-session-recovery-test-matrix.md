# 星盾 Android 会话恢复验收矩阵

## 目标与边界

- 登录最长保留时间由刷新令牌控制，当前上限为 180 天；Access Token 仍为 7 天，UserSig 仍按服务端短周期签发。
- 客户端不保存账号密码。有效刷新令牌可换取新 Access Token 和 UserSig；刷新令牌过期或服务端撤销会话后必须重新登录。
- 请求遇到 401 时只允许刷新一次、重放一次，禁止递归重试。

## 自动化回归

| 场景 | 前置状态 | 预期结果 | 覆盖测试 |
| --- | --- | --- | --- |
| 正常冷启动 | Access Token、UserSig 均有效 | 直接恢复缓存会话并进入主界面 | `XingDunSessionRestorePolicyTest.coldStartUsesCachedCredentialsWhileBothAreUsable` |
| 旧版注册会话冷启动 | 无刷新令牌、Access Token 有效、UserSig 过期 | 使用 Access Token 续签 UserSig，不清除登录态 | `XingDunSessionRestorePolicyTest.legacyRegistrationSessionRenewsExpiredUserSigWithValidAccessToken` |
| 长时间未启动 | Access Token 或 UserSig 过期、180 天刷新令牌仍有效 | 轮换刷新令牌并恢复完整会话 | `XingDunSessionRestorePolicyTest.validRefreshTokenRenewsEntireSessionAfterLongIdle` |
| 到达 180 天边界 | 刷新令牌在当前时刻到期 | 判定会话过期并要求重新登录 | `XingDunSessionRestorePolicyTest.refreshTokenAtExpiryBoundaryIsRejected` |
| 普通请求首次 401 | 当前账号刷新令牌有效 | 刷新一次，用新 Access Token 重放原请求一次 | `XingDunAuthenticatedRequestExecutorTest.unauthorizedRequestRefreshesAndReplaysExactlyOnce` |
| 重放后再次 401 | 刷新成功但重放仍未授权 | 不再重试，清理当前会话并进入登录页 | `XingDunAuthenticatedRequestExecutorTest.secondUnauthorizedRejectsSessionWithoutThirdAttempt` |
| 服务端撤销会话 | 刷新接口返回未授权 | 不重放业务请求，清理当前会话并进入登录页 | `XingDunAuthenticatedRequestExecutorTest.rejectedRefreshEndsSessionWithoutReplay` |
| 业务请求断网 | 请求抛出网络异常 | 保留本地会话，不刷新、不退出登录 | `XingDunAuthenticatedRequestExecutorTest.offlineFailureKeepsSessionAndDoesNotAttemptRecovery` |
| 401 后刷新时断网 | 刷新接口抛出网络异常 | 保留本地会话，不重放、不退出登录 | `XingDunAuthenticatedRequestExecutorTest.offlineDuringRefreshKeepsSessionAndDoesNotReplay` |
| 并发请求同时 401 | 同一账号、同一旧 Access Token | 只执行一次刷新，其余请求复用新会话 | `XingDunAccessTokenRecoveryCoordinatorTest.concurrentUnauthorizedRequestsShareOneRefresh` |
| 切换账号后的旧请求返回 401 | 请求账号与当前账号不同 | 旧请求不得借用或清除新账号会话 | `XingDunAccessTokenRecoveryCoordinatorTest.staleRequestCannotRecoverWithAnotherAccountSession` |
| 新注册响应 | 账号或手机号注册成功 | 响应包含刷新令牌，过期时间为 180 天 | `XingDunAuthenticationContractTest.registrationAuthenticationResponseParses180DayRefreshCredential`、服务端 `auth_session_refresh_contract_test.php` |

## 真机与联调验收

| 场景 | 操作 | 验收结果 |
| --- | --- | --- |
| 保留数据升级后冷启动 | 覆盖安装当前 Debug APK，强停后启动 | TAS-AN00 已进入 `MainActivity`，未出现启动崩溃 |
| 新账号注册后跨 UserSig 周期恢复 | 部署服务端改动后注册测试账号，等待或缩短测试环境 UserSig 周期，再冷启动 | 待受控测试账号与服务端环境验收 |
| 180 天内长期恢复 | 在测试环境构造接近到期但仍有效的刷新令牌，冷启动 | 自动化已覆盖时间边界；真机联调待测试环境可控时钟或令牌 |
| 断网冷启动与请求 | 关闭网络后冷启动及触发鉴权请求，随后恢复网络重试 | 自动化已覆盖不清会话；真机网络切换待验收 |
| 管理端撤销会话 | 登录测试账号后提升 `auth_version` 或执行撤销接口，再发起请求 | 自动化已覆盖退出策略；真机联调待专用测试账号 |
| 401 并发重放 | 让多个接口同时使用失效 Access Token | 自动化已覆盖单次刷新；真机联调待可控测试令牌 |

真机待验收项不得使用生产用户或当前用户会话直接制造过期、撤销或切换账号；需使用专用测试账号和可控测试环境。
