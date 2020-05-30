package ktx.telegram.generator

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File
import java.io.IOException
import java.util.Scanner

object FileTools {
    fun parseJavaFile(codeString: String, fileName: String) =
        PsiManager.getInstance(project).findFile(
            LightVirtualFile(fileName, JavaFileType.INSTANCE, codeString)
        ) as PsiJavaFile

    @Throws(IOException::class)
    fun File.asString(): String {
        val fileContents = StringBuilder(this.length().toInt())
        Scanner(this).use { scanner ->
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine() + System.lineSeparator())
            }
            return fileContents.toString()
        }
    }

    private val project by lazy {
        val configuration = CompilerConfiguration()
        configuration.put(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(
                System.err,
                MessageRenderer.PLAIN_RELATIVE_PATHS,
                false
            )
        )
        KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        ).project
    }
}