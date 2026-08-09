package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 奖品组件定义表的 MyBatis-Flex 映射。 */
@Table("prize_component")
data class PrizeComponentEntity(
    /** 奖品组件主键；固定奖品组件固定为 1。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 奖品组件类型，FIXED 或 EXTENSION。 */
    var type: String = "EXTENSION",
    /** 奖品组件唯一编码。 */
    var code: String = "",
    /** 奖品组件展示名称。 */
    var name: String = "",
    /** 奖品组件用途说明。 */
    var description: String? = null,
    /** 扩展奖品字段定义 JSON；固定奖品组件固定为空定义。 */
    @Column("definition_json")
    var definitionJson: String = "{}",
    /** 是否允许新奖励组件挂载。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
