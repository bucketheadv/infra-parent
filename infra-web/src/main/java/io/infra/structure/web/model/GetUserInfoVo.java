package io.infra.structure.web.model;

import lombok.Data;

import java.util.List;

@Data
public class GetUserInfoVo {
    private int rlt;

    private String uid;

    private List<CustomFieldDTO> data;

    private String modify_cb;
}
