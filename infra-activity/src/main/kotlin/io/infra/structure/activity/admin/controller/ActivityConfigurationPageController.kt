package io.infra.structure.activity.admin.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** 活动配置管理页面控制器。 */
@Controller
class ActivityConfigurationPageController {

    /** 返回模板组件配置页面。 */
    @GetMapping("/activity/component")
    fun component(model: Model): String = configurationPage(model, "components")

    /** 返回奖励组件配置页面。 */
    @GetMapping("/activity/reward/component")
    fun rewardComponent(model: Model): String = configurationPage(model, "rewardComponents")

    /** 返回奖品组件配置页面。 */
    @GetMapping("/activity/reward/prize")
    fun prizeComponent(model: Model): String = configurationPage(model, "prizeComponents")

    /** 返回活动模板配置页面。 */
    @GetMapping("/activity/template")
    fun template(model: Model): String = configurationPage(model, "templates")

    /** 返回奖励模板配置页面。 */
    @GetMapping("/activity/reward/template")
    fun rewardTemplate(model: Model): String = configurationPage(model, "rewardTemplates")

    /** 返回活动配置页面。 */
    @GetMapping("/activity/config")
    fun activity(model: Model): String = configurationPage(model, "activities")

    /** 返回可复用任务定义配置页面。 */
    @GetMapping("/activity/task/definition")
    fun taskDefinition(): String = "activity-task-definition"

    /** 返回活动模板与任务定义的绑定配置页面。 */
    @GetMapping("/activity/task/binding")
    fun taskBinding(): String = "activity-task-binding"

    /** 返回活动生成的任务实例和执行记录页面。 */
    @GetMapping("/activity/task/instance")
    fun taskInstance(): String = "activity-task-instance"

    /** 保留旧入口，并跳转至第一个任务配置页面。 */
    @GetMapping("/activity/task")
    fun task(): String = "redirect:/activity/task/definition"

    /** 标记当前页面应显示的配置区域，模板和脚本保持复用。 */
    private fun configurationPage(model: Model, activeSection: String): String {
        val metadata = configurationPageMetadata(activeSection)
        model.addAttribute("activeSection", activeSection)
        model.addAttribute("pageTitle", metadata.title)
        model.addAttribute("pageDescription", metadata.description)
        return "activity-config"
    }

    /** 根据当前配置区域提供浏览器标题、左侧标题与页面说明。 */
    private fun configurationPageMetadata(activeSection: String): ConfigurationPageMetadata = when (activeSection) {
        "components" -> ConfigurationPageMetadata("模板组件配置", "创建可复用的输入组件与子组件结构。")
        "rewardComponents" -> ConfigurationPageMetadata("奖励组件配置", "配置奖励字段、奖品组件与挂载规则。")
        "prizeComponents" -> ConfigurationPageMetadata("奖品类型配置", "维护固定奖品与可扩展奖品字段。")
        "templates" -> ConfigurationPageMetadata("活动模板配置", "组合活动组件与奖励模板，形成活动配置蓝图。")
        "rewardTemplates" -> ConfigurationPageMetadata("奖励模板配置", "编排奖励组件和奖品字段，供活动模板挂载。")
        "activities" -> ConfigurationPageMetadata("活动配置", "根据活动模板创建并维护具体活动。")
        else -> ConfigurationPageMetadata("活动配置", "从组件定义到模板编排，再按模板创建具体活动。")
    }

    /** 活动配置页面展示所需的静态标题信息。 */
    private data class ConfigurationPageMetadata(
        /** 当前页面的名称。 */
        val title: String,
        /** 当前页面的简短说明。 */
        val description: String
    )
}
