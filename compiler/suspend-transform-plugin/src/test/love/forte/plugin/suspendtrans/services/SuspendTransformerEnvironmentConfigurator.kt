package love.forte.plugin.suspendtrans.services

import love.forte.plugin.suspendtrans.SuspendTransformComponentRegistrar
import love.forte.plugin.suspendtrans.SuspendTransformConfiguration
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

/**
 * Inject SuspendTransform plugin into test environment
 */
class SuspendTransformerEnvironmentConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {

    @OptIn(ExperimentalCompilerApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration
    ) {
        // register plugin
        SuspendTransformComponentRegistrar.register(this, SuspendTransformConfiguration().apply {
            useDefault()
        })
    }

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        configuration.put(JVMConfigurationKeys.NO_JDK, false)
        configuration.configureJdkClasspathRoots()

        // register runtimes
        getRuntimeJarFile("love.forte.plugin.suspendtrans.runtime.RunInSuspendJvmKt")?.let {
            configuration.addJvmClasspathRoot(
                it
            )
        }
        getRuntimeJarFile("love.forte.plugin.suspendtrans.annotation.JvmAsync")?.let {
            configuration.addJvmClasspathRoot(
                it
            )
        }
        getRuntimeJarFile("love.forte.plugin.suspendtrans.annotation.JvmBlocking")?.let {
            configuration.addJvmClasspathRoot(
                it
            )
        }

        // register coroutines
        getRuntimeJarFile("kotlinx.coroutines.CoroutineScope")?.let {
            configuration.addJvmClasspathRoot(
                it
            )
        }
    }

    private fun getRuntimeJarFile(className: String): File? {
        try {
            return getRuntimeJarFile(Class.forName(className))
        } catch (_: ClassNotFoundException) {
            System.err.println("Runtime jar '$className' not found!")
//            assert(false) { "Runtime jar '$className' not found!" }
        }
        return null
    }

    private fun getRuntimeJarFile(clazz: Class<*>): File {
//        try {
            return PathUtil.getResourcePathForClass(clazz)
//        } catch (e: ClassNotFoundException) {
//            System.err.println("Runtime jar '$clazz' not found!")
////            assert(false) { "Runtime jar '$className' not found!" }
//        }
//        return null
    }

}
