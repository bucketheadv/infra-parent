# infra-rocketmq-admin

RocketMQ 消息管理后台（独立部署）。依赖 `infra-rocketmq` 提供生产者/消费者基建，本模块注册管理 REST 与 Thymeleaf 页面，通过 `rocketmq-tools` 的 `DefaultMQAdminExt` 访问 NameServer 与 Broker。

## 启动

```bash
export ROCKETMQ_NAMESRV_ADDR='127.0.0.1:9876'
export ROCKETMQ_ADMIN_AUTH_ENABLED='true'
export ROCKETMQ_ADMIN_ACCESS_TOKEN='...'        # 管理页面访问 API 的令牌
export ROCKETMQ_ADMIN_ACCESS_KEY='...'           # 集群开启 ACL 时必填
export ROCKETMQ_ADMIN_SECRET_KEY='...'
mvn -pl infra-rocketmq-admin -am spring-boot:run
```

默认端口 `18082`（可用 `ROCKETMQ_ADMIN_PORT` 覆盖），避免与调度管理后台（18080）、调度执行器示例（18081）冲突。

## 功能

- 仪表盘：Broker / Topic / 消费组 / 客户端连接概览
- Topic 管理：列表、详情（队列位点）、新建、删除
- 消费组：订阅列表、消费进度（含堆积量）、客户端连接、按时间重置位点
- 消息：按 Topic / Key / 时间范围检索，消息详情（正文文本/十六进制、系统属性），死信重发与测试消息发送

## 配置项

前缀 `infra.rocketmq.admin`，默认值见 `src/main/resources/application.yml`：

| 配置项 | 说明 |
| --- | --- |
| `enabled` | 是否启用本模块（默认 true） |
| `namesrv-addr` | NameServer 地址 |
| `access-key` / `secret-key` | 集群开启 ACL 时使用 |
| `use-tls` | 是否启用 TLS |
| `operation-timeout-millis` | 管理操作超时（默认 5000） |
| `auth-enabled` / `access-token` | 是否开启管理 API 令牌校验 |

管理 REST 位于 `/api/rocketmq/**`。`auth-enabled=true` 时需携带 `X-Infra-RocketMQ-Admin-Token`，页面会自动附加。

## 安全

管理 API 支持删除 Topic、重置位点、重发与发送消息，属于高危操作。**生产环境务必设置 `auth-enabled=true` 与强 `access-token`，并限制该端口仅内网访问。**

## 其他模块接入

本模块以 Spring Boot 自动配置提供（`AutoConfiguration.imports`）。其他 Spring Boot 应用可直接依赖本模块并配置上述前缀，无需复制页面代码。