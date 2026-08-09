package io.infra.structure.activity.admin.service

import io.infra.structure.activity.admin.dto.PrizeLookupResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Service

/** 默认奖品中心实现，用于本地联调；接入真实奖品中心时提供 PrizeCatalogGateway 实现即可替换。 */
@Service
//@ConditionalOnMissingBean(PrizeCatalogGateway::class)
class DemoPrizeCatalogGateway : PrizeCatalogGateway {

    /** 返回可继续编辑的演示奖品属性。 */
    override fun query(prizeType: String, prizeId: String): PrizeLookupResponse? {
        if (prizeId.isBlank()) {
            return null
        }
        val typeName = when (prizeType) {
            "DECORATION" -> "装扮"
            "GIFT" -> "礼物"
            else -> return null
        }
        return PrizeLookupResponse(
            prizeType = prizeType,
            prizeId = prizeId.trim(),
            prizeName = "$typeName #${prizeId.trim()}",
            prizeIcon = "https://example.com/prizes/${prizeType.lowercase()}/${prizeId.trim()}.png",
            prizeValue = "0",
            prizeDisplayValue = "0",
            prizeQuantity = "1"
        )
    }
}
