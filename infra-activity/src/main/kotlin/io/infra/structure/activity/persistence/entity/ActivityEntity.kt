package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 依据活动模板创建的活动配置主表映射。 */
@Table("activity")
data class ActivityEntity(
    /** 活动主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 活动展示名称。 */
    var name: String = "",
    /** 创建活动时采用的模板主键。 */
    @Column("template_id")
    var templateId: Long = 0,
    /** 活动状态，例如 DRAFT 或 ACTIVE。 */
    var status: String = "DRAFT",
    /** 上下线状态，取值为 ONLINE 或 OFFLINE。 */
    @Column("online_status")
    var onlineStatus: String = "OFFLINE",
    /** 是否不设置结束时间而永久有效。 */
    @Column("valid_forever")
    var validForever: Boolean = true,
    /** 非永久活动的生效开始时间戳，单位为毫秒。 */
    @Column("valid_start_time")
    var validStartTime: Long? = null,
    /** 非永久活动的生效结束时间戳，单位为毫秒。 */
    @Column("valid_end_time")
    var validEndTime: Long? = null,
    /** 是否启用仅面向白名单用户的调试模式。 */
    @Column("debug_mode")
    var debugMode: Boolean = false,
    /** 调试模式允许访问的用户主键 JSON 数组。 */
    @Column("debug_user_ids_json")
    var debugUserIdsJson: String = "[]",
    /** 调试模式下强制使用的时间戳，单位为毫秒。 */
    @Column("debug_force_time")
    var debugForceTime: Long? = null,
    /** 按模板字段键保存的活动配置 JSON。 */
    @Column("form_data_json")
    var formDataJson: String = "{}",
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 最后更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
