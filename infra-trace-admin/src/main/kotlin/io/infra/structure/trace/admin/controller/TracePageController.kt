package io.infra.structure.trace.admin.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * 追踪后台页面路由。
 *
 * @author sven
 */
@Controller
class TracePageController {

    /** 链路列表页。 */
    @GetMapping("/")
    fun dashboard(model: Model): String {
        model.addAttribute("pageTitle", "链路追踪")
        model.addAttribute("pageName", "traces")
        return "trace-dashboard"
    }

    /** 服务拓扑页。 */
    @GetMapping("/topology")
    fun topology(model: Model): String {
        model.addAttribute("pageTitle", "服务拓扑")
        model.addAttribute("pageName", "topology")
        return "trace-topology"
    }

    /** 单条链路详情页（瀑布图）。 */
    @GetMapping("/traces/{traceId}")
    fun detail(@PathVariable traceId: String, model: Model): String {
        model.addAttribute("pageTitle", "链路详情")
        model.addAttribute("pageName", "detail")
        model.addAttribute("traceId", traceId)
        return "trace-detail"
    }
}
