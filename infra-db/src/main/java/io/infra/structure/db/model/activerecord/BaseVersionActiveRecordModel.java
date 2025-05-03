package io.infra.structure.db.model.activerecord;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.core.activerecord.Model;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author sven
 * Created on 2025/1/18 21:06
 */
@Getter
@Setter
public class BaseVersionActiveRecordModel<M extends Model<M>, ID extends Serializable> extends BaseActiveRecordModel<M, ID> {
    /**
     * 数据版本号
     */
    @Column(version = true)
    private Integer version;
}
