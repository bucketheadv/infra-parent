package org.infra.structure.web.dao;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author sven
 * Created on 2025/1/16 13:35
 */
@Table("user_info")
@Data(staticConstructor = "create")
@EqualsAndHashCode(callSuper = true)
public class UserPo extends Model<UserPo> {
    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;
}
