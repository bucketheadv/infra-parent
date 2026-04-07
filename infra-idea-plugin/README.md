# infra-idea-plugin

`infra-idea-plugin` 是一个 IntelliJ IDEA 插件模块，用来让 IDEA 识别 `infra-redis`、`infra-rocketmq` 这类“由 properties 动态生成 Bean”的配置。

这个模块是独立的 Gradle 插件工程，当前没有挂到仓库根 `pom.xml` 的 Maven reactor 中。

当前实现支持：

- 从 `.properties`、`.yml`、`.yaml` 扫描 Redis 动态 Bean
- 从 `.properties`、`.yml`、`.yaml` 扫描 RocketMQ 动态 Bean
- 在 `@Qualifier`、`@Named`、`@Resource(name=...)` 中补全动态 Bean 名称
- 在这些注解里的 Bean 名称上支持跳转到对应 properties
- 在字段、构造器参数、属性注入位置显示 gutter 图标，点击可跳转到 Bean 配置
- 当 `@Qualifier` 指向不存在的动态 Bean 时给出检查提示
- 当 Spring 报出 “无法自动装配 / Could not autowire” 且对应动态 Bean 在配置中存在时，自动消除这类误报

## 已支持的动态 Bean 规则

### Redis

- `infra.redis.template.main.master.host` -> `mainJedisTemplate`
- `infra.redis.template.order.mode=cluster` -> `orderJedisClusterTemplate`

### RocketMQ

- `infra.rocketmq.producers.order.namesrvAddr` -> `orderRocketMQProducer`
- `infra.rocketmq.consumers.core.namesrvAddr` -> `coreRocketMQConsumerFactory`

## 构建

在模块目录执行：

```bash
gradle buildPlugin
```

构建完成后，插件 zip 位于：

```text
build/distributions/
```

## 安装

1. 打开 IDEA
2. `Settings` -> `Plugins`
3. 选择 `Install Plugin from Disk...`
4. 选择 `build/distributions` 下生成的 zip

## 当前限制

- 重点支持注入场景，暂未覆盖业务代码中的所有字符串参数导航
- 依赖 IntelliJ Platform 2024.1.x
