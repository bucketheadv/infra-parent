package org.infra.structure.db.model.activerecord;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.activerecord.Model;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * @author sven
 * Created on 2025/1/18 21:05
 */
@Getter
@Setter
public class BaseModel<M extends Model<M>, ID extends Serializable> extends Model<M> {
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
