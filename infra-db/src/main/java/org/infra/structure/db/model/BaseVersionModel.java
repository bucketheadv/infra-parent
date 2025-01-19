package org.infra.structure.db.model;

import com.mybatisflex.annotation.Column;
import lombok.*;

import java.io.Serializable;

/**
 * @author sven
 * Created on 2025/1/18 21:03
 */
@Getter
@Setter
public class BaseVersionModel<ID extends Serializable> extends BaseModel<ID> {
    /**
     * 数据版本号
     */
    @Column(version = true)
    private Integer version;
}
