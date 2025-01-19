package org.infra.structure.db.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * @author sven
 * Created on 2025/1/18 21:00
 */
@Getter
@Setter
public abstract class BaseModel<ID extends Serializable> {
    /**
     * 主键id
     */
    @Id(keyType = KeyType.Auto)
    private ID id;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
