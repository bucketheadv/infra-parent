package io.infra.structure.db.model.activerecord

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.core.activerecord.Model
import io.infra.structure.db.model.DbEntity
import java.io.Serializable

/**
 * @author liuqinglin
 * Date: 2025/5/2 22:33
 */
abstract class BaseActiveRecordEntity<T : Model<T>, ID : Serializable> (
    /**
     * 主键id
     */
    @Id(keyType = KeyType.Auto)
    override var id: ID?,

    /**
     * 创建时间
     */
    var createTime: Long?,

    /**
     * 更新时间
     */
    var updateTime: Long?

) : Model<T>(), DbEntity<ID> {
    constructor(): this(null, null, null)
}