//package io.infra.structure.script.service;
//
//import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
//import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
//import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
//import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
//import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
//import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
//import org.jetbrains.kotlin.config.CompilerConfiguration;
//import org.springframework.stereotype.Service;
//
//import javax.tools.DiagnosticCollector;
//import javax.tools.JavaFileObject;
//import javax.tools.SimpleJavaFileObject;
//import javax.tools.ToolProvider;
//import java.io.File;
//import java.io.IOException;
//import java.net.URI;
//import java.net.URLClassLoader;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//import java.util.concurrent.Callable;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.concurrent.ExecutionException;
//
//@Service
//public class UniversalCodeExecutor {
//    private static final long TIMEOUT_SECONDS = 10L;
//    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "universal_code_executor");
//    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\s*;");
//    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile("\\s*(public|private|protected|static|final|abstract|strictfp)*\\s*" +
//            "(public|private|protected|static|final|abstract|strictfp)*\\s*" +
//            "class\\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\\s*(implements|extends|\\{|)");
//    private static final Pattern KOTLIN_CLASS_PATTERN = Pattern.compile("\\s*(class|object)\\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\\s*(\\(|implements|extends|:|\\{|)");
//
//    public String execute(String code, String language) {
//        if (code == null || code.trim().isEmpty()) return "代码不能为空";
//        Path tempDir = createTempDirectory();
//        try {
//            switch (language.toLowerCase()) {
//                case "java":
//                    return executeJava(code, tempDir);
//                case "kotlin":
//                    return executeKotlin(code, tempDir);
//                default:
//                    return "不支持的语言: " + language;
//            }
//        } catch (Exception e) {
//            return "执行错误: " + e.getMessage();
//        } finally {
//            deleteDirectory(tempDir);
//        }
//    }
//
//    private String executeJava(String code, Path tempDir) {
//        String className = extractJavaClassName(code);
//        if (className == null) return "无法提取Java类名";
//        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
//        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
//        JavaFileObject fileObject = new JavaSourceFromString(className, code);
//        javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
//            null, null, diagnostics,
//            List.of("-d", tempDir.toString(), "-classpath", getClassPath()),
//            null, List.of(fileObject)
//        );
//        if (!task.call()) {
//            return "编译错误:\n" + diagnostics.getDiagnostics().stream()
//                .map(d -> d.getMessage(null))
//                .reduce((a, b) -> a + "\n" + b)
//                .orElse("");
//        }
//        return loadAndExecuteClass(className, tempDir);
//    }
//
//    private String executeKotlin(String code, Path tempDir) {
//        String className = extractKotlinClassName(code);
//        if (className == null) className = "KotlinCode";
//        Path kotlinFile = tempDir.resolve(className + ".kt");
//        try {
//            Files.write(kotlinFile, code.getBytes());
//        } catch (IOException e) {
//            return "写入Kotlin文件失败: " + e.getMessage();
//        }
//        List<String> errors = compileKotlin(kotlinFile, tempDir);
//        if (!errors.isEmpty()) {
//            return "编译错误:\n" + String.join("\n", errors);
//        }
//        String actualClassName = findKotlinMainClass(tempDir, className);
//        if (actualClassName == null) actualClassName = className;
//        return loadAndExecuteClass(actualClassName, tempDir);
//    }
//
//    private List<String> compileKotlin(Path kotlinFile, Path outputDir) {
//        CompilerConfiguration configuration = new CompilerConfiguration();
//        List<String> messages = new ArrayList<>();
//        MessageCollector messageCollector = new MessageCollector() {
//            @Override
//            public void report(CompilerMessageSeverity severity, String message, CompilerMessageSourceLocation location) {
//                if (severity == CompilerMessageSeverity.ERROR) messages.add(message);
//            }
//            @Override
//            public boolean hasErrors() {
//                return !messages.isEmpty();
//            }
//            @Override
//            public void clear() {
//                messages.clear();
//            }
//        };
//        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector);
//        K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
//        arguments.setFreeArgs(List.of(kotlinFile.toString()));
//        arguments.setDestination(outputDir.toString());
//        arguments.setClasspath(getClassPath());
//        K2JVMCompiler compiler = new K2JVMCompiler();
//        int exitCode = compiler.exec(messageCollector, org.jetbrains.kotlin.config.Services.EMPTY, arguments).ordinal();
//        if (exitCode != 0 || messageCollector.hasErrors()) {
//            return messages;
//        }
//        return List.of();
//    }
//
//    private String loadAndExecuteClass(String className, Path tempDir) {
//        URLClassLoader classLoader;
//        try {
//            classLoader = URLClassLoader.newInstance(new java.net.URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader());
//        } catch (java.net.MalformedURLException e) {
//            return "类加载失败: " + e.getMessage();
//        }
//        Class<?> clazz;
//        try {
//            clazz = classLoader.loadClass(className);
//        } catch (ClassNotFoundException e) {
//            String actualClass = findActualClass(tempDir);
//            if (actualClass != null) {
//                return loadAndExecuteClass(actualClass, tempDir);
//            }
//            return "类未找到: " + className;
//        }
//        if (!Callable.class.isAssignableFrom(clazz)) {
//            return "代码必须实现java.util.concurrent.Callable接口";
//        }
//        var executor = Executors.newSingleThreadExecutor();
//        var future = executor.submit(() -> {
//            try {
//                Callable<?> instance = (Callable<?>) clazz.getDeclaredConstructor().newInstance();
//                Object result = instance.call();
//                return "" + (result != null ? result : "null");
//            } catch (Exception e) {
//                return "执行异常: " + e.getMessage();
//            }
//        });
//        try {
//            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
//        } catch (TimeoutException e) {
//            future.cancel(true);
//            return "执行超时: 代码运行时间超过 " + TIMEOUT_SECONDS + " 秒";
//        } catch (InterruptedException | ExecutionException e) {
//            return "执行异常: " + e.getMessage();
//        } finally {
//            executor.shutdownNow();
//        }
//    }
//
//    private String extractJavaClassName(String code) {
//        Matcher packageMatcher = JAVA_PACKAGE_PATTERN.matcher(code);
//        String packageName = packageMatcher.find() ? packageMatcher.group(1) : null;
//        Matcher classMatcher = JAVA_CLASS_PATTERN.matcher(code);
//        if (!classMatcher.find()) return null;
//        String className = classMatcher.group(3);
//        return packageName != null ? packageName + "." + className : className;
//    }
//
//    private String extractKotlinClassName(String code) {
//        Matcher matcher = KOTLIN_CLASS_PATTERN.matcher(code);
//        return matcher.find() ? matcher.group(2) : null;
//    }
//
//    private String findKotlinMainClass(Path tempDir, String baseName) {
//        try {
//            return Files.walk(tempDir)
//                .filter(p -> p.toString().endsWith(".class"))
//                .map(p -> {
//                    String relativePath = p.toString().substring(tempDir.toString().length() + 1);
//                    return relativePath.replace(File.separator, ".")
//                        .substring(0, relativePath.length() - 6);
//                })
//                .filter(name -> name.startsWith(baseName) && !name.contains("$"))
//                .findFirst()
//                .orElse(null);
//        } catch (IOException e) {
//            return null;
//        }
//    }
//
//    private String findActualClass(Path tempDir) {
//        try {
//            return Files.walk(tempDir)
//                .filter(p -> p.toString().endsWith(".class"))
//                .map(p -> {
//                    String relativePath = p.toString().substring(tempDir.toString().length() + 1);
//                    return relativePath.replace(File.separator, ".")
//                        .substring(0, relativePath.length() - 6);
//                })
//                .findFirst()
//                .orElse(null);
//        } catch (IOException e) {
//            return null;
//        }
//    }
//
//    private Path createTempDirectory() {
//        try {
//            Files.createDirectories(TEMP_DIR);
//            return Files.createTempDirectory(TEMP_DIR, "exec-");
//        } catch (IOException e) {
//            throw new RuntimeException("创建临时目录失败", e);
//        }
//    }
//
//    private void deleteDirectory(Path dir) {
//        try {
//            Files.walk(dir)
//                .sorted(Comparator.reverseOrder())
//                .map(Path::toFile)
//                .forEach(File::delete);
//        } catch (IOException e) {
//            // 忽略删除失败
//        }
//    }
//
//    private String getClassPath() {
//        return System.getProperty("java.class.path");
//    }
//
//    private static class JavaSourceFromString extends SimpleJavaFileObject {
//        private final String code;
//
//        JavaSourceFromString(String name, String code) {
//            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
//            this.code = code;
//        }
//
//        @Override
//        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
//            return code;
//        }
//    }
//}