package io.infra.structure.logging

import org.springframework.stereotype.Component

/**
 * PolyLog AOP 测试用组件。
 * outer 与 inner 通过两个 Bean 互相调用，确保都经过 Spring AOP 代理。
 */
@Component
class PolyLogTestComponent {

    @PolyLog("outer")
    fun outer() {
        LogContext.instance().log("外层开始")
        LogContext.info("外层信息")
    }

    @PolyLog("inner")
    fun inner() {
        LogContext.instance().log("内层日志")
    }

    @PolyLog(value = "inner-new", propagation = PolyLogPropagation.REQUIRES_NEW)
    fun innerNew() {
        LogContext.instance().log("新开日志流")
    }

    @PolyLog("outer")
    fun outerCallsInner(inner: PolyLogTestComponent) {
        LogContext.instance().log("外层-前")
        inner.inner()
        LogContext.instance().log("外层-后")
    }

    @PolyLog("outer")
    fun outerCallsInnerNew(inner: PolyLogTestComponent) {
        LogContext.instance().log("外层-前")
        inner.innerNew()
        LogContext.instance().log("外层-后")
    }

    @PolyLog("outer")
    fun outerThrows() {
        LogContext.instance().log("异常前的日志")
        throw IllegalStateException("模拟异常")
    }
}
