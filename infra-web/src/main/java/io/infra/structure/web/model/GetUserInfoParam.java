package io.infra.structure.web.model;

import lombok.Data;

@Data
public class GetUserInfoParam {
    private String appid;

    private String token;

    private String userid;

    private String staffphone;
}
