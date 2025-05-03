package io.infra.structure.db.model

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import java.io.Serializable
import java.util.Date

/**
 * @author liuqinglin
 * Date: 2025/5/3 00:05
 */
abstract class BaseEntity<ID : Serializable>(
    /**
     * 主键id
     */
    @Id(keyType = KeyType.Auto)
    override var id: ID?,

    /**
     * 创建时间
     */
    var createTime: Date?,

    /**
     * 更新时间
     */
    var updateTime: Date?
) : DbEntity<ID>