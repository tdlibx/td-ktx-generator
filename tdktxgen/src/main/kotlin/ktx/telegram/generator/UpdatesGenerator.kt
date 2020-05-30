package ktx.telegram.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import ktx.telegram.generator.TdApiKtxGenerator.createParameterType
import org.jetbrains.kotlin.com.intellij.psi.PsiClass

object UpdatesGenerator {
    fun mapUpdatesByFile(funcs: List<FunSpec>): HashMap<String, List<FunSpec>> {

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
                            MemberName(
                                "kotlinx.coroutines.flow",
                                "mapNotNull"
                            )
                        )
                    } ?: addStatement(
                    "return this.getUpdatesFlowOfType()"
                )

                createUpdateDoc(update)
            }
            .returns(
                ClassName(
                    "kotlinx.coroutines.flow",
                    "Flow"
                )
                    .parameterizedBy(
                        update.fields.first()
                            .takeIf { update.fields.size == 2 }
                            ?.type
                            ?.let {
                                createParameterType(
                                    it
                                ).copy(nullable = false)
                            }
                            ?: ClassName(
                                TD_API_PACKAGE,
                                update.name.toString()
                            )
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


}