package io.infra.structure.web.model;

import lombok.Data;

@Data
public class CustomFieldDTO {
    private Integer index;

    private String key;

    private String label;

    private Object value;

    private Boolean edit;

    private String map;

    private String href;

    private Boolean select;
}
