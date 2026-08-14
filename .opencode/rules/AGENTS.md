# Infra Structure OpenCode 协作规范

本文件为 OpenCode 项目级规范，参照 `.codex/AGENTS.md`（Codex 协作规范）编写并适配当前 `infra-parent` 仓库。处理本仓库任务时，遵循以下规范；如用户指令与本文件冲突，以用户指令为准。

## 仓库概览

- Maven 多模块仓库：父工程 `io.infra.structure:infra-parent`，继承 `com.github.bucketheadv:infra-pom`。
- 模块按职责拆分：`infra-core`、`infra-db`、`infra-redis`、`infra-rocketmq`、`infra-logging`、`infra-schedule`、`infra-schedule-admin`、`infra-sso`、`infra-sso-login`、`infra-job`、`infra-api`、`infra-activity`、`infra-doc`、`infra-script` 等。
- 技术栈：Kotlin（主语言，与 Java 混编）、Spring Boot、MyBatis-Flex、MySQL、Redis、RocketMQ、Ktor、Lombok、Jackson。
- 编译方式：kotlin-maven-plugin 先编译 `src/main/kotlin`，maven-compiler-plugin 再编译 `src/main/java`（混编仓库，勿单独改动编译顺序）。
- 基础包名 `io.infra.structure.*`；源码位于各模块 `src/main/kotlin` 与 `src/main/java`。
- 构建验证：根目录执行 `mvn -pl <模块> -am compile` 或 `mvn -q compile`；测试使用 `mvn -pl <模块> -am test`。

## 沟通与改动原则

- 与用户交互一律使用中文：对话回复、交付说明、Commit 信息、文档注释均使用中文；代码标识符、注释遵循仓库既有惯例。
- 回复保持专业、简洁、友好；先给技术结论或可执行方案。若用户明确要求只要技术结论，不添加寒暄或鼓励。
- 面对故障、报错、回滚或紧急修复，先用一句简短的共情回应，再提供可执行方案。
- 最终回复在合适时追加一句简短鼓励，避免模板化、空泛或喧宾夺主。
- 只做与当前需求直接相关的最小改动；不得顺手重构无关模块。
- 优先复用现有的分层、命名、工具、异常与查询模式，避免风格漂移。
- 未经明确要求不得新增第三方依赖；优先使用 JDK、Spring 和仓库既有组件。
- 不得硬编码密码、密钥、Token 或账号；配置应来自配置中心、环境变量或既有安全配置机制。
- 接口、消息或字段语义变更时，在交付说明中注明兼容性影响。
- 除非用户明确要求，不主动新增测试文件或测试代码；业务逻辑变更后仍应给出可执行的本地验证步骤。

## 后端基线（适用于 `infra-*/src/main/kotlin/**/*.kt` 与 `infra-*/src/main/java/**/*.java`）

- 后端核心栈为 Kotlin、Spring Boot、MyBatis-Flex、MySQL 和 Maven；遵循可读性、稳定性和 Spring 分层实践。
- 代码按 `controller`、`service`、`repository`/`dao`、`dto`、`config`、`util`、`domain` 等职责组织在 `io.infra.structure.*` 下。
- 严格保持 `controller -> service -> repository/dao` 分层：Controller 仅做参数校验、鉴权入口和响应组装；Service 负责业务编排与领域规则；DAO 仅负责数据访问和查询拼装。
- 事务优先声明在 Service 层，禁止在 Controller 或 DAO 层承载事务边界。
- 对外 DTO 与持久化实体解耦，不直接暴露数据库实体。
- Controller 使用 `@RestController` 和 `@RequestMapping`；Service 使用 `@Service`；DAO 使用 `@Repository`；配置类使用 `@Configuration`。
- 类使用 PascalCase，函数和变量使用 camelCase，常量使用 UPPER_SNAKE_CASE，包名全小写。禁止通配符导入；导入顺序为标准库、第三方、项目内部。
- ORM 统一使用 MyBatis-Flex，优先沿用已有 DAO/Mapper 查询写法。数据库字段使用下划线命名；主键为 `id`（BIGINT），时间字段为 `create_time`、`update_time`。
- 时间处理优先沿用所在模块既有惯例（Joda Time 或 `java.time`），不跨模块引入不一致的时间 API。请求与响应使用对象封装，避免散落的原始参数。
- 保持 Kotlin 空安全，避免滥用 `!!`；方法命名应表达业务语义，不保留未使用的方法。发现重复逻辑时优先提取复用方法。

## API、异常与日志（适用于含对外接口的模块：`infra-api`、`infra-activity`、`infra-sso-login`、`infra-schedule-admin` 等）

- API 使用 RESTful 风格、统一响应结构和恰当的 HTTP 状态码；路径使用小写和 `/` 分隔，不使用连字符、下划线或驼峰。
- 外部入参必须进行类型、范围、长度、枚举和格式校验（例如 `@Valid`）；分页接口应支持分页参数，并对白名单化排序字段。
- 使用统一业务错误语义，禁止向调用方暴露底层 SQL、内部路径或堆栈。业务异常继承 `RuntimeException` 并由全局异常处理器集中处理。
- 禁止吞掉异常：要么有明确降级，要么携带业务上下文后重新抛出。
- 使用 SLF4J + Logback 记录关键业务操作；不得记录密码、Token、密钥或敏感原文，身份证、手机号、邮箱等按需脱敏。

## 复杂业务与性能（适用于各模块 Kotlin/Java 代码）

- 多校验、多读写或多分支的业务拆分为"编排方法 + 步骤方法"。编排方法加载必要数据、串联步骤、控制事务并返回结果；步骤方法保持单一职责，命名如 `validateXxx`、`buildXxx`、`applyXxx`。
- 编排层应一次加载数据并通过参数或上下文对象传递给步骤。除非依赖前序结果且不能提前获知，否则步骤内不得重复查询同一数据；远程调用和缓存读取同样优先在编排层完成。
- 简单单查单写的 CRUD 无须为拆分而拆分；参数过多时使用小型上下文对象。
- 同一请求中相同主键或相同条件的数据只查询一次并复用结果。禁止在 Controller、Service、步骤或 VO 组装链路中重复 `findById` 或相同列表查询。
- 禁止循环逐条查库（N+1）；使用批量查询并在内存关联。列表查询仅加载当前页所需字段与关联，避免无界加载、全表扫描、无索引条件查询和大结果集二次过滤。
- 写路径也避免重复的"先查再查"；可以使用已加载数据或条件更新时，不再重复读取。
- 关注函数长度（尽量不超过 50 行）、类大小（尽量不超过 500 行）和方法复杂度。

## Redis（适用于 `infra-redis` 及使用 Redis 的模块）

- Key 使用 `模块.场景.实体.标识` 的业务命名空间，禁止无前缀裸 Key。
- 除非业务明确要求，缓存 Key 必须设置 TTL。
- 优先采用 cache-aside：读写路径一致，写数据库后同步删除或更新缓存，避免长期脏读。
- 防缓存穿透和击穿：空值使用短 TTL；热点 Key 采用互斥重建或逻辑过期。
- 避免大 Key 和超大集合；必要时拆分并监控热点。

## 前端基线（适用于 `infra-*/src/main/resources/static/**/*.{js,css,html}` 及模板中的内联脚本）

- 使用 `const` 声明不会重新赋值的变量，使用 `let` 声明需要重新赋值的变量；除非必要（如需要函数级提升语义）否则禁止使用 `var`。
- 原生 JavaScript 以 `(function () { "use strict"; })()` 包裹，避免污染全局作用域；DOM 就绪后再绑定事件。
- 使用 `class` 属性与事件委托绑定动态列表的事件，避免为每行重复绑定监听器。
- 网络请求使用 `fetch`，错误统一提示，不抛出未捕获异常；服务端返回的文本一律转义后再插入 DOM，防止 XSS。

## 配置（适用于 `infra-*/src/main/resources/**/*.{yml,yaml,properties}`）

- 本地、测试、生产配置必须隔离；默认配置不得包含生产地址。
- 密码、密钥和 Token 不得以明文提交；使用密文或外部密钥管理。
- 超时、重试和连接池大小必须显式配置。新增开关必须提供默认值与回滚策略，并保持配置命名语义一致。

## 测试与发布安全（适用于各模块源码）

- 用户要求补测试时优先覆盖核心业务路径；修复线上问题时优先补充可复现问题的回归测试。
- 涉及接口字段、状态机或消息事件的改动，说明兼容策略和灰度风险。

## 接口安全（适用于含对外接口的模块 API 变更）

- 按"最不信任调用方"设计。身份必须由服务端会话或 Token 解析，禁止将请求体、Query 或 Header 中的 `uid`/`userId` 作为操作主体；代操作需明确授权。无效、过期或校验失败的 Token 一律拒绝。
- 鉴权后仍须校验功能权限、角色和数据范围。每个读写接口都应明确访问者与资源范围，默认拒绝、显式放行；按资源 ID 操作必须验证资源归属或组织范围，列表和导出接口也必须做数据范围过滤。
- 校验所有外部输入，拒绝超大请求、超深嵌套和非法格式；恶意或畸形参数只能得到受控业务错误，不能造成未捕获异常、500 堆栈或服务不可用。
- SQL 使用参数绑定或 MyBatis-Flex 条件构造，禁止拼接用户输入；Mongo、Redis 或脚本查询也不得拼接原始用户输入。动态字段、操作符、排序字段、文件名、URL 和 JSON 路径必须使用预定义白名单。
- 登录、验证码、密码重置和短信等敏感接口要限流、失败锁定或冷却。抽奖、发券、下单、扣减、支付回调等影响资金或库存的操作必须具备幂等、防重放、频控与审计能力；回调/Webhook 必须验签并验证金额、订单号和状态机。
- 关键写操作校验状态机并使用版本控制、条件更新或事务保证并发安全，避免重复生效、超发或篡改。
- 响应最小化，禁止返回密码、Token、密钥、完整证件号等；批量查询和导出需要权限及数量上限。错误不得泄露账号是否存在等可枚举细节。
- 服务端请求外部 URL 时防 SSRF，禁止用户指定任意内网地址。上传文件必须校验类型、大小和内容，且隔离存储与访问路径。管理和调试接口不得在生产暴露，危险开关默认关闭。
- 改接口时自检：服务端身份、功能权限和数据范围是否都已验证；恶意输入是否受控；是否存在注入、越权、拖库、刷接口或资损路径；响应和日志是否已脱敏。

## 交付检查

- 变更 Kotlin/Java 后，按受影响模块执行适当的 Maven 编译、测试或最小验证（`mvn -pl <模块> -am compile` 等）；未运行时说明原因。
- 对接口改动说明验证方式、兼容性与安全/灰度影响；必要时给出接口、SQL、日志或关键路径的本地验证步骤。
