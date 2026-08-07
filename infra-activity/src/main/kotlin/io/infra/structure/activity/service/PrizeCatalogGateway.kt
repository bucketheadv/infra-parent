package io.infra.structure.activity.service

import io.infra.structure.activity.web.configuration.PrizeLookupResponse

/** 奖品中心查询网关；业务系统可替换此 Bean 对接真实奖品接口。 */
interface PrizeCatalogGateway {

    /** 根据奖品类型和唯一标识查询奖品属性。 */
    fun query(prizeType: String, prizeId: String): PrizeLookupResponse?
}
