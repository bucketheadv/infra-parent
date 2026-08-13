package io.infra.structure.schedule.admin.web

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/** 将管理 API 的数据库约束错误转换为可供页面直接展示的冲突原因。 */
@RestControllerAdvice(basePackageClasses = [ScheduleAdminController::class])
class ScheduleAdminApiExceptionHandler {
    /** 保留业务冲突的明确原因，避免 Spring 默认错误页只返回 "Conflict"。 */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(exception: ResponseStatusException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            exception.statusCode,
            exception.reason ?: exception.statusCode.toString()
        )

    /** 不向管理页面泄漏完整 SQL 与堆栈，同时保留管理员可操作的失败原因。 */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ProblemDetail {
        val detail = exception.mostSpecificCause.message.orEmpty()
        val reason = when {
            "executor_id" in detail && "cannot be null" in detail.lowercase() ->
                "任务必须绑定有效执行器后才能保存或启用"
            "uk_infra_schedule_executor_group" in detail || "executor_group" in detail ->
                "执行器分组已存在，请使用其他分组标识"
            "fk_infra_schedule_job_executor" in detail ->
                "执行器仍被任务引用，不能删除"
            else -> "操作与当前数据状态冲突，请刷新页面后重试"
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, reason)
    }
}
