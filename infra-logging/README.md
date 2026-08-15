# infra-logging 使用说明

## 概述

`infra-logging` 提供日志输出增强能力，支持：

- 敏感字段日志脱敏（`password`、`token`、证件号、手机号、邮箱等自动掩码）
- 控制台日志彩色输出（级别、线程、Logger、类、消息分词着色）
- `@PolyLog` 日志流收集：按传播策略归入同一条日志流统一输出

## 敏感字段日志脱敏

对日志消息中形如 `field=value`、`field: value`、`"field":"value"` 的敏感字段进行自动掩码，开箱即用，无需业务代码改动。

### 默认脱敏策略

| 字段 | 示例 | 输出 |
| --- | --- | --- |
| 口令类 `password/passwd/pwd/secret/secretKey/apiKey/accessToken/refreshToken/token` | `password=123456` | `password=******` |
| 证件类 `idCard/idCardNo/idNumber/certNo/ssn` | `idCard=110101199003078888` | `idCard=1101**********8888` |
| 手机号 `mobile/mobileNo/phone/phoneNo` | `mobile=13812345678` | `mobile=138****5678` |
| 邮箱 `email` | `email=test@example.com` | `email=t***@example.com` |

字段名匹配不区分大小写。

### 使用方式

默认已生效，`logback-spring.xml` 中消息部分为：

```xml
<conversionRule conversionWord="mask"
                class="io.infra.structure.logging.mask.FieldMaskConverter"/>
...
<pattern>... %msgColor(%mask(%msg)) %n</pattern>
```

业务代码无需任何改动，例如：

```kotlin
log.info("登录成功 user={} password={}", user, password)
// 实际输出：登录成功 user=admin password=******
```

### 追加自定义脱敏字段

通过 `application.yml` 追加额外敏感字段（命中时整体掩码）：

```yaml
log:
  mask:
    extra-fields: accountNo,cardNo,cvv
```

### 关闭脱敏

```yaml
log:
  mask:
    enabled: false
```

### 相关类

- `io.infra.structure.logging.mask.FieldMasker`：脱敏核心逻辑，可独立调用
- `io.infra.structure.logging.mask.FieldMaskConverter`：logback 转换器
- `io.infra.structure.logging.mask.MaskRule`：脱敏规则定义

## 彩色日志

默认控制台 pattern 注册了以下转换器：

- `levelColor`：级别着色
- `threadColor`：线程名着色
- `loggerColor`：Logger 名着色
- `classColor`：调用文件与行号着色
- `msgColor`：消息按变量/关键字分词着色

## PolyLog 日志流

在类或方法上标注 `@PolyLog`，其中通过 `LogContext.instance().log()` 收集的日志会按前缀归入同一条日志流，在 `flush()` 时统一输出。

```kotlin
@PolyLog("order")
class OrderService {
    fun create() {
        val ctx = LogContext.instance()
        ctx.log("开始创建订单")
        // ... 业务逻辑
        ctx.flush()
    }
}
```

支持 `REQUIRED`（默认，复用外层日志流）与 `REQUIRES_NEW`（新开独立日志流，结束时立即 flush）两种传播策略。

## 相关类

- `io.infra.structure.logging.autoconfiguration.InfraLoggingAutoConfiguration`
- `io.infra.structure.logging.LogContext`
- `io.infra.structure.logging.PolyLog`
- `io.infra.structure.logging.aspect.PolyLogAspect`