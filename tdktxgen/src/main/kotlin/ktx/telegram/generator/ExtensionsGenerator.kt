package ktx.telegram.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.nio.file.Paths

object ExtensionsGenerator {
    fun generateExtensionClasses(functions: List<FunSpec>): List<TypeSpec> {
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
            scopes + TypeSpec.interfaceBuilder(
                "BaseKtx"
            ).addModifiers(KModifier.PUBLIC)
                .addProperty(
                    PropertySpec.builder(API_FIELD_NAME, ClassName(CORE_PACKAGE, API_NAME))
                        .addKdoc(API_FIELD_DOC)
                        .build()
                )
                .addKdoc("Base extensions interface")
                .build() + TypeSpec.interfaceBuilder("TelegramKtx")
                .addModifiers(KModifier.PUBLIC)
                .addKdoc("Interface for access all Telegram objects extension " +
                    "functions. Contains ${scopes.sumBy { it.funSpecs.count() }} extensions")
                .addProperty(
                    PropertySpec.builder(
                        API_FIELD_NAME,
                        ClassName(CORE_PACKAGE, API_NAME),
                        KModifier.OVERRIDE
                    )
                        .addKdoc(API_FIELD_DOC)
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

    private const val API_FIELD_DOC = "Instance of the [$API_NAME] connecting extensions to the Telegram " +
        "Client"

    private fun createScope(
        objectName: String, functions: List<FunSpec>
    ): TypeSpec {
        return TypeSpec.interfaceBuilder(
            objectName + "Ktx"
        )
            .addKdoc("Interface for access " +
                if (objectName == "Common") "common" else "[TdApi.$objectName]" +
                " extension functions. Can be" +
                " used alongside with other extension interfaces of the package. Must contain " +
                "[$API_NAME] instance field to access its functionality")
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(
                ClassName(
                    SCOPE_PACKAGE,
                    "BaseKtx"
                )
            )
            .addProperty(
                PropertySpec.builder(
                    API_FIELD_NAME,
                    ClassName(
                        CORE_PACKAGE,
                        API_NAME
                    ),
                    KModifier.OVERRIDE
                )
                    .addKdoc(API_FIELD_DOC)
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

    fun writeClassesFiles(
        classes: List<TypeSpec>,
        packageName: String
    ) {
        classes.forEach { cls ->
            val file = FileSpec.builder(
                packageName = packageName, //.${function.getResultName().decapitalize()}
                fileName = cls.name.toString()
            )
                .addComment(
                    "\n" +
                        "NOTE: THIS FILE IS AUTO-GENERATED by the %S.kt\n" +
                        "See: https://github.com/tdlibx/td-ktx-generator/\n" +
                        "", this::class.java.simpleName
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
}