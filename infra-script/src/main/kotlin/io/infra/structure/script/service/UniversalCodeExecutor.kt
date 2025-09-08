package io.infra.structure.script.service

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

/**
 * @author liuqinglin
 * Date: 2025/5/31 13:14
 */
@Service
class UniversalCodeExecutor {
    fun execute(code: String?, language: String): Any? {
        if (code == null || code.trim { it <= ' ' }.isEmpty()) return "代码不能为空"
        val tempDir = createTempDirectory()
        return try {
            when (language.lowercase(Locale.getDefault())) {
                "java" -> executeJava(code, tempDir)
                "kotlin" -> executeKotlin(code, tempDir)
                else -> "不支持的语言: $language"
            }
        } catch (e: Exception) {
            "执行错误: " + e.message
        } finally {
            deleteDirectory(tempDir)
        }
    }

    private fun executeJava(code: String, tempDir: Path): Any? {
        val className = extractJavaClassName(code) ?: return "无法提取Java类名"
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject?>()
        val fileObject: JavaFileObject = JavaSourceFromString(className, code)
        val task = compiler.getTask(
            null, null, diagnostics,
            listOf<String?>("-d", tempDir.toString(), "-classpath", this.classPath),
            null, listOf<JavaFileObject?>(fileObject)
        )
        if (!task.call()) {
            return "编译错误:\n" + diagnostics.getDiagnostics().stream()
                .map { d: Diagnostic<out JavaFileObject?>? -> d!!.getMessage(null) }
                .reduce { a: String?, b: String? -> a + "\n" + b }
                .orElse("")
        }
        return loadAndExecuteClass(className, tempDir)
    }

    private fun executeKotlin(code: String, tempDir: Path): Any? {
        var className = extractKotlinClassName(code)
        if (className == null) className = "KotlinCode"
        val kotlinFile = tempDir.resolve("$className.kt")
        try {
            Files.write(kotlinFile, code.toByteArray())
        } catch (e: IOException) {
            return "写入Kotlin文件失败: " + e.message
        }
        val errors = compileKotlin(kotlinFile, tempDir)
        if (!errors.isEmpty()) {
            return "编译错误:\n" + java.lang.String.join("\n", errors)
        }
        var actualClassName = findKotlinMainClass(tempDir, className)
        if (actualClassName == null) actualClassName = className
        return loadAndExecuteClass(actualClassName, tempDir)
    }

    private fun compileKotlin(kotlinFile: Path, outputDir: Path): MutableList<String?> {
        val configuration = CompilerConfiguration()
        val messages: MutableList<String?> = ArrayList<String?>()
        val messageCollector: MessageCollector = object : MessageCollector {
            override fun report(
                severity: CompilerMessageSeverity,
                message: String,
                location: CompilerMessageSourceLocation?
            ) {
                if (severity == CompilerMessageSeverity.ERROR) messages.add(message)
            }

            override fun hasErrors(): Boolean {
                return !messages.isEmpty()
            }

            override fun clear() {
                messages.clear()
            }
        }
        configuration.put(MESSAGE_COLLECTOR_KEY, messageCollector)
        val arguments = K2JVMCompilerArguments()
        arguments.freeArgs = listOf(kotlinFile.toString())
        arguments.destination = outputDir.toString()
        arguments.classpath = this.classPath
        val compiler = K2JVMCompiler()
        val exitCode = compiler.exec(messageCollector, Services.Companion.EMPTY, arguments).ordinal
        if (exitCode != 0 || messageCollector.hasErrors()) {
            return messages
        }
        return mutableListOf()
    }

    private fun loadAndExecuteClass(className: String?, tempDir: Path): Any? {
        val classLoader: URLClassLoader
        try {
            classLoader = URLClassLoader.newInstance(arrayOf<URL>(tempDir.toUri().toURL()), javaClass.getClassLoader())
        } catch (e: MalformedURLException) {
            return "类加载失败: " + e.message
        }
        val clazz: Class<*>
        try {
            clazz = classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {
            val actualClass = findActualClass(tempDir)
            if (actualClass != null) {
                return loadAndExecuteClass(actualClass, tempDir)
            }
            return "类未找到: $className"
        }
        if (!Callable::class.java.isAssignableFrom(clazz)) {
            return "代码必须实现java.util.concurrent.Callable接口"
        }
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit(Callable<Any?> submit@{
            try {
                val instance = clazz.getDeclaredConstructor().newInstance() as Callable<*>
                val result: Any? = instance.call()
                return@submit result
            } catch (e: Exception) {
                return@submit "执行异常: " + e.message
            }
        })
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            return "执行超时: 代码运行时间超过 $TIMEOUT_SECONDS 秒"
        } catch (e: InterruptedException) {
            return "执行异常: " + e.message
        } catch (e: ExecutionException) {
            return "执行异常: " + e.message
        } finally {
            executor.shutdownNow()
        }
    }

    private fun extractJavaClassName(code: String): String? {
        val packageMatcher: Matcher = JAVA_PACKAGE_PATTERN.matcher(code)
        val packageName = if (packageMatcher.find()) packageMatcher.group(1) else null
        val classMatcher: Matcher = JAVA_CLASS_PATTERN.matcher(code)
        if (!classMatcher.find()) return null
        val className = classMatcher.group(3)
        return if (packageName != null) "$packageName.$className" else className
    }

    private fun extractKotlinClassName(code: String): String? {
        val matcher: Matcher = KOTLIN_CLASS_PATTERN.matcher(code)
        return if (matcher.find()) matcher.group(2) else null
    }

    private fun findKotlinMainClass(tempDir: Path, baseName: String): String? {
        return try {
            Files.walk(tempDir)
                .filter { p: Path? -> p.toString().endsWith(".class") }
                .map { p: Path? ->
                    val relativePath = p.toString().substring(tempDir.toString().length + 1)
                    relativePath.replace(File.separator, ".")
                        .substring(0, relativePath.length - 6)
                }
                .filter { name: String? -> name!!.startsWith(baseName) && !name.contains("$") }
                .findFirst()
                .orElse(null)
        } catch (_: IOException) {
            null
        }
    }

    private fun findActualClass(tempDir: Path): String? {
        return try {
            Files.walk(tempDir)
                .filter { p: Path? -> p.toString().endsWith(".class") }
                .map { p: Path? ->
                    val relativePath = p.toString().substring(tempDir.toString().length + 1)
                    relativePath.replace(File.separator, ".")
                        .substring(0, relativePath.length - 6)
                }
                .findFirst()
                .orElse(null)
        } catch (_: IOException) {
            null
        }
    }

    private fun createTempDirectory(): Path {
        try {
            Files.createDirectories(TEMP_DIR)
            return Files.createTempDirectory(TEMP_DIR, "exec-")
        } catch (e: IOException) {
            throw RuntimeException("创建临时目录失败", e)
        }
    }

    private fun deleteDirectory(dir: Path) {
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map { obj: Path? -> obj!!.toFile() }
                .forEach { obj: File? -> obj!!.delete() }
        } catch (_: IOException) {
            // 忽略删除失败
        }
    }

    private val classPath: String?
        get() = System.getProperty("java.class.path")

    private class JavaSourceFromString(name: String, private val code: String?) : SimpleJavaFileObject(
        URI.create("string:///" + name.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
        JavaFileObject.Kind.SOURCE
    ) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence? {
            return code
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private val TEMP_DIR: Path = Paths.get(System.getProperty("java.io.tmpdir"), "universal_code_executor")
        private val JAVA_PACKAGE_PATTERN: Pattern =
            Pattern.compile("\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\s*;")
        private val JAVA_CLASS_PATTERN: Pattern = Pattern.compile(
            "\\s*(public|private|protected|static|final|abstract|strictfp)*\\s*" +
                    "(public|private|protected|static|final|abstract|strictfp)*\\s*" +
                    "class\\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\\s*(implements|extends|\\{|)"
        )
        private val KOTLIN_CLASS_PATTERN: Pattern =
            Pattern.compile("\\s*(class|object)\\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\\s*(\\(|implements|extends|:|\\{|)")
    }
}