package io.infra.structure.db.model

import java.io.Serializable


/**
 * @author liuqinglin
 * Date: 2025/5/2 22:28
 */
interface DbEntity<ID : Serializable> : Serializable {
    /**
     * 主键id
     */
    var id: ID?

}