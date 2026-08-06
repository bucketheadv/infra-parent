package io.infra.structure.activity.web.configuration

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** 活动配置管理页面控制器。 */
@Controller
class ActivityConfigurationPageController {

    /** 返回模板组件配置页面。 */
    @GetMapping("/activity/component")
    fun component(model: Model): String = configurationPage(model, "components")

    /** 返回活动模板配置页面。 */
    @GetMapping("/activity/template")
    fun template(model: Model): String = configurationPage(model, "templates")

    /** 返回活动配置页面。 */
    @GetMapping("/activity/config")
    fun activity(model: Model): String = configurationPage(model, "activities")

    /** 标记当前页面应显示的配置区域，模板和脚本保持复用。 */
    private fun configurationPage(model: Model, activeSection: String): String {
        model.addAttribute("activeSection", activeSection)
        return "activity-config"
    }
}
