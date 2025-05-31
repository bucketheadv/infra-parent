package io.infra.structure.script.controller

import io.infra.structure.script.service.UniversalCodeExecutor
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * @author liuqinglin
 * Date: 2025/5/30 17:44
 */
@RestController
@RequestMapping("/code")
class CodeController(
    private val codeExecutionService: UniversalCodeExecutor,
) {
    @RequestMapping("call", method = [RequestMethod.POST])
    fun call(@RequestParam("code") code: String, @RequestParam("language", defaultValue = "java") language: String): Any? {
        return codeExecutionService.execute(code, language)
    }
}