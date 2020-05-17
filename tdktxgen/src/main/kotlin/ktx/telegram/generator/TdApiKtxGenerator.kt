package ktx.telegram.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_RELATIVE_PATHS
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.ide.highlighter.JavaFileType
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiArrayType
import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.PsiType
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.Scanner

fun main() {
    generateDocs()
}

fun getTelegramApiFile(): File {
    return File(apiPath)
}

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

val currentPath: String = System.getProperty("user.dir")
val apiPath = "$currentPath/../td/libtd/src/main/java/org/drinkless/td/libcore/telegram/TdApi.java"
val outputPath = "$currentPath/../td-flow/libtd-ktx/src/main/java"

private const val FUNCTIONS_PACKAGE = "kotlinx.telegram.coroutines"

private const val SCOPE_PACKAGE = "kotlinx.telegram.extensions"

fun generateDocs() {
    println("Generating docs")
    val file = getTelegramApiFile()
    val codeString = file.asString()
    val ktFile: PsiJavaFile =
        createKtFile(codeString, file.name)
    val tdapi: PsiClass = ktFile.classes[0]
    val allClasses: Array<PsiClass> = tdapi.innerClasses
    println(tdapi.docComment?.descriptionElements?.map { it.text })

    val javaFunctions = allClasses.filter {
        it.extendsList?.referencedTypes?.firstOrNull()?.name == "Function"
    }

    objects = allClasses.filter {
        it.extendsList?.referencedTypes?.firstOrNull()?.name == "Object"
    }

    val javaUpdates = allClasses.filter {
        it.extendsList?.referencedTypes?.firstOrNull()?.name == "Update"
    }

    val functions = translateJavaToKt(
        javaFunctions,
        ::generateFunction
    )

    val functionsFilesMap =
        mapFunctionsByFile(functions)

    writeExtFuncFilesByMap(
        functionsFilesMap,
        FUNCTIONS_PACKAGE,
        "FunctionsKtx"
    )


    functionsFilesMap.filter { it.value.size == 1 }.forEach {
        println("${it.key} - ${it.value.first().name}")
    }

    val updates = translateJavaToKt(
        javaUpdates,
        ::generateUpdate
    )

    val updatesFileMap =
        mapUpdatesByFile(updates)

    println(updatesFileMap.map { "${it.key}: ${it.value.joinToString { it.name }}" })

    writeExtFuncFilesByMap(
        updatesFileMap,
        "kotlinx.telegram.flows",
        "UpdatesKtx"
    )

    val repositories =
        collectUpdateAndFuctions(
            functions,
            updates
        )

    writeClassesFiles(
        repositories,
        SCOPE_PACKAGE
    )
}

private const val API_FIELD_NAME = "api"

fun collectUpdateAndFuctions(functions: List<FunSpec>, updates: List<FunSpec>): List<TypeSpec> {
    val commonExts = ArrayList<FunSpec>()
    return objects.mapNotNull { obj ->
        val objectName = obj.name.toString()
        val objectId = objectName.decapitalize() + "Id"
        val exts = functions.filter {
            it.parameters.any {
                it.name == objectId
            } && obj.fields.any { it.name == "id" }
        }
        if (exts.size == 1) {
            commonExts.add(
                createObjectExtensionFunctionById(
                    exts.first(),
                    objectName,
                    objectId
                )
            )
        }

        val functions = exts.map { extension ->
            createObjectExtensionFunctionById(
                extension,
                objectName,
                objectId
            )
        }
        createScope(objectName, functions).takeIf { exts.size > 1 }

    }.let { scopes ->
        scopes + createScope(
            "Common",
            commonExts
        )

    }.let { scopes ->
        scopes + TypeSpec.interfaceBuilder("BaseKtx")
            .addModifiers(KModifier.PUBLIC)
            .addProperty(
                PropertySpec.builder(
                    API_FIELD_NAME,
                    ClassName(
                        CORE_PACKAGE,
                        API_NAME
                    )
                ).build()
            )
            .build() + TypeSpec.interfaceBuilder("TelegramKtx")
            .addModifiers(KModifier.PUBLIC)
            .addProperty(
                PropertySpec.builder(
                    API_FIELD_NAME,
                    ClassName(
                        CORE_PACKAGE,
                        API_NAME
                    ),
                    KModifier.OVERRIDE
                )
                    .build()
            )
            .addSuperinterfaces(scopes.map {
                ClassName(
                    SCOPE_PACKAGE,
                    it.name.toString()
                )
            })
            .build()
    }
}

private fun createScope(
    objectName: String, functions: List<FunSpec>
): TypeSpec {
    return TypeSpec.interfaceBuilder(objectName + "Ktx")
        .addModifiers(KModifier.PUBLIC)
        .addSuperinterface(ClassName(SCOPE_PACKAGE, "BaseKtx"))
        .addProperty(
            PropertySpec.builder(
                API_FIELD_NAME,
                ClassName(
                    CORE_PACKAGE,
                    API_NAME
                ),
                KModifier.OVERRIDE
            )
                .build()
        )
        .apply {
            functions.forEach {
                addFunction(it)
            }
        }.build()
}

private fun createObjectExtensionFunctionById(
    extension: FunSpec, objectName: String, objectIdField: String
): FunSpec {
    return FunSpec.builder(extension.name.replace(objectName, ""))
        .receiver(ClassName(TD_API_PACKAGE, objectName))
        .addModifiers(KModifier.PUBLIC)
        .addModifiers(KModifier.SUSPEND)
        .apply {
            val params = extension.parameters.map {
                it.takeIf { it.name != objectIdField }
            }
            params.filterNotNull().forEach {
                addParameter(it)
            }
            addStatement(
                "return $API_FIELD_NAME.%M" +
                    "(${params.joinToString {
                        it?.name ?: "this.id"
                    }})", MemberName(FUNCTIONS_PACKAGE, extension.name)
            )
        }.addKdoc(extension.kdoc.toString()
            .replace("@return [", "@return [TdApi.")
            .split("\n")
            .filter { !it.startsWith("@param $objectIdField") }
            .joinToString("\n")
        )
        .build()
}

private const val CORE_PACKAGE = "kotlinx.telegram.core"

private const val API_NAME = "TelegramApi"

private fun writeExtFuncFilesByMap(
    filesMap: HashMap<String, List<FunSpec>>,
    packageName: String,
    nameSuffix: String
) {
    filesMap.forEach { (name, functions) ->
        val file = FileSpec.builder(
            packageName = packageName, //.${function.getResultName().decapitalize()}
            fileName = name + nameSuffix
        )
            .addComment(
                "\n" +
                    "NOTE: THIS FILE IS AUTO-GENERATED by the %S.kt\n" +
                    "See: https://github.com/JetBrains/kotlin/tree/master/libraries/stdlib\n" +
                    "", "GenerateTelegramLib"
            )
            .addImport("org.drinkless.td.libcore.telegram", "TdApi")
            .addImport(
                CORE_PACKAGE,
                API_NAME
            )
            .apply {
                functions.forEach { addFunction(it) }
            }
            .build()

        file.writeTo(Paths.get(outputPath))
    }
}

private fun writeClassesFiles(
    classes: List<TypeSpec>,
    packageName: String
) {
    classes.forEach { cls ->
        val file = FileSpec.builder(
            packageName = packageName, //.${function.getResultName().decapitalize()}
            fileName = cls.name.toString()
        )
            .addImport("org.drinkless.td.libcore.telegram", "TdApi")
            .addImport(
                CORE_PACKAGE,
                API_NAME
            )
            .addType(cls)
            .build()

        file.writeTo(Paths.get(outputPath))
    }
}

private fun mapUpdatesByFile(funcs: List<FunSpec>): HashMap<String, List<FunSpec>> {

    val filesMap = HashMap<String, List<FunSpec>>()
    val etc =
        objects.mapNotNull { it.name }
            .sortedBy { it.count() }
            .filter { it != "Text" }
            .fold(
                funcs.toMutableList()

            ) { f, name ->
                val cont = f.filter { it.name.capitalize().contains(name) }
                if (cont.size > 1) {
                    filesMap[name] = cont
                    f.removeAll(cont)
                }
                f
            }

    filesMap["Common"] = etc

    return filesMap
}

private fun mapFunctionsByFile(funcs: List<FunSpec>): HashMap<String, List<FunSpec>> {

    val filesMap = HashMap<String, List<FunSpec>>()
    val etc = listOf(
        "PhoneNumber", "Email", "Password",
        "Log", "Network", "Authentication",
        "Query", "Database", "Payment", "Passport",
        "Language", "Account", "Emoji"
    ).plus(
        objects.mapNotNull { it.name }
            .sortedBy { it.count() }
            .filter { it != "Text" }
    ).fold(
        funcs.toMutableList()

    ) { f, name ->
        val cont = f.filter { it.name.contains(name) }
        if (cont.size > 1) {
            filesMap[name] = cont
            f.removeAll(cont)
        }
        f
    }

    //    etc.forEach {
    //        val upper = it.name.indexOfFirst { it.isUpperCase() }
    //        filesMap[
    //            if (upper > 0) it.name.substring(upper) else it.name.capitalize()
    //        ] = listOf(it)
    //    }
    filesMap["Common"] = etc

    return filesMap
}

private fun translateJavaToKt(
    classes: List<PsiClass>,
    convert: (PsiClass) -> FunSpec
): List<FunSpec> {
    return classes.map { function ->
        val func = convert(function)
        println(func)
        func
    }
}

private fun PsiClass.getResultName(): String {
    return this.docComment?.text
        ?.substringAfterLast("{@link ")
        ?.substringBefore("} </p>")
        ?.split(" ")
        ?.first() ?: throw Exception("Result name not found ${this.name}")
}

private const val TD_API_PACKAGE = "org.drinkless.td.libcore.telegram.TdApi"

fun generateFunction(
    function: PsiClass
): FunSpec {
    val result = objects.firstOrNull { it.name == function.getResultName() }
        ?: throw java.lang.Exception("Result object not found ${function.getResultName()}")

    val resultName = result.name.toString()
    return FunSpec.builder(function.name.toString().decapitalize())
        .receiver(
            ClassName(
                CORE_PACKAGE,
                API_NAME
            )
        )
        .addModifiers(KModifier.PUBLIC)
        .addModifiers(KModifier.SUSPEND)
        .apply {
            val parameters =
                createFunctionParameters(
                    function
                )
            parameters.forEach { addParameter(it) }
            val suspendSender = if (resultName == "Ok") {
                "sendFunctionLaunch"
            } else {
                returns(ClassName(TD_API_PACKAGE, resultName))
                "sendFunctionAsync"
            }
            addStatement(
                "return this.$suspendSender(TdApi.${function.name.toString()}" +
                    "(${parameters.joinToString { it.name }}))"
            )

            createFunctionDoc(function, result)

        }
        .build()
}

fun generateUpdate(
    update: PsiClass
): FunSpec {
    return FunSpec.builder(
        update.name.toString().removePrefix("Update").decapitalize() + "Flow"
    )
        .receiver(
            ClassName(
                CORE_PACKAGE,
                API_NAME
            )
        )
        .addModifiers(KModifier.PUBLIC)
        .apply {
            update.fields.first()
                .takeIf { update.fields.size == 2 }
                ?.let {
                    addStatement(
                        "return this.getUpdatesFlowOfType<TdApi.${update.name}>()" +
                            "\n    .%M { it.${it.name.toString()} }",
                        MemberName("kotlinx.coroutines.flow", "mapNotNull")
                    )
                } ?: addStatement(
                "return this.getUpdatesFlowOfType()"
            )

            createUpdateDoc(update)
        }
        .returns(
            ClassName("kotlinx.coroutines.flow", "Flow")
                .parameterizedBy(
                    update.fields.first()
                        .takeIf { update.fields.size == 2 }
                        ?.type
                        ?.let {
                            createParameterType(
                                it
                            ).copy(nullable = false)
                        }
                        ?: ClassName(TD_API_PACKAGE, update.name.toString())
                )
        )
        .build()
}

private fun FunSpec.Builder.createUpdateDoc(
    update: PsiClass
) {
    update.docComment
        ?.descriptionElements
        ?.firstOrNull { it.text.isNotBlank() }
        ?.let {
            addKdoc("emits ${
            update.fields.first()
                .takeIf { update.fields.size == 2 }
                ?.let {
                    if (it.name != it.type.presentableText.decapitalize())
                        "${it.name} [${it.type.presentableText.capitalize()}"
                    else "[${it.type.presentableText.capitalize()}"
                }
                ?: "[${update.name.toString()}"
            }] if ${it.text.trim().decapitalize()}")
        }
}

private fun FunSpec.Builder.createFunctionDoc(
    function: PsiClass,
    result: PsiClass
) {
    val javaDoc = function.constructors.asList().lastOrNull()?.docComment

    val docs = javaDoc?.descriptionElements
        ?.map { it.text.trim() }
        ?.filter { it.isNotBlank() } as? MutableList

    docs?.firstOrNull()?.let {
        docs[0] = it.replace("Creates a function", "Suspend function")
            .replace("Default constructor for a function", "Suspend function")
    }

    docs?.removeAll { it.startsWith("<p>") }
    docs?.removeAll { it.startsWith("{@link") }
    docs?.removeAll { it.startsWith("</p>") }

    if (docs != null) {
        addKdoc(
            docs.joinToString("\n")
        )
    }

    addKdoc("\n\n")
    javaDoc?.tags?.let {
        addKdoc(it.joinToString("\n") {
            "${it.nameElement.text} ${it.dataElements.joinToString(" ") { it.text }}"
        })
        if (it.isNotEmpty()) addKdoc("\n\n")
    }
    if (result.name.toString() != "Ok")
        addKdoc(
            "@return [${result.name.toString()}] ${
            result.docComment?.descriptionElements?.getOrNull(1)?.text?.trim() ?: ""
            }"
        )
}

private fun createFunctionParameters(
    function: PsiClass
): List<ParameterSpec> {

    return function.fields.filter { it.name != "CONSTRUCTOR" }.map {
        val nullable = it.annotations.firstOrNull()?.text == "@Nullable"
        ParameterSpec.builder(
            name = it.name.toString(),
            type = createParameterType(it.type)
        ).also {
            if (nullable) it.defaultValue("null")
        }.build()
    }
}

fun createParameterType(t: PsiType): TypeName {

    return when (t) {
        is PsiArrayType -> {
            val comp = t.componentType
            return when (comp.presentableText) {
                "byte", "long", "int" ->
                    ClassName("kotlin", "${comp.presentableText.capitalize()}Array")
                "String" -> ClassName("kotlin", "Array").parameterizedBy(
                    ClassName(
                        "kotlin",
                        comp.presentableText.capitalize()
                    )
                )
                else -> ClassName("kotlin", "Array").parameterizedBy(
                    ClassName(
                        TD_API_PACKAGE,
                        comp.presentableText.capitalize()
                    )
                )
            }.copy(nullable = true)
        }

        is PsiClassReferenceType -> if (t.presentableText == "String")
            ClassName("kotlin", t.presentableText).copy(nullable = true)
        else
            ClassName(TD_API_PACKAGE, t.presentableText).copy(nullable = true)
        else -> ClassName("kotlin", t.presentableText.capitalize())
    }
}

fun createKtFile(codeString: String, fileName: String) =
    PsiManager.getInstance(project)
        .findFile(
            LightVirtualFile(fileName, JavaFileType.INSTANCE, codeString)
        ) as PsiJavaFile

var objects: List<PsiClass> = emptyList()

private val project by lazy {
    val configuration = CompilerConfiguration()
    configuration.put(
        CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        PrintingMessageCollector(System.err, PLAIN_RELATIVE_PATHS, false)
    )
    KotlinCoreEnvironment.createForProduction(
        Disposer.newDisposable(),
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    ).project
}