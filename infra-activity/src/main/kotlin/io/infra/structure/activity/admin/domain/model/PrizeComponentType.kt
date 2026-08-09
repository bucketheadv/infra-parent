package io.infra.structure.activity.admin.domain.model

/** 奖品组件类型。 */
enum class PrizeComponentType {
    /** 系统预置且不可变更的固定奖品组件。 */
    FIXED,
    /** 在固定奖品字段之外增加业务自定义字段的扩展奖品组件。 */
    EXTENSION
}
