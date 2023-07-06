package me.rerere.unocssintellij.documentation

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.model.Pointer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.*
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssLazyStylesheet
import com.intellij.psi.css.impl.util.CssHighlighter
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.createSmartPointer
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.toHex

class UnocssDocumentTargetProviderOffset : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
        val service = file.project.service<UnocssService>()
        val element: PsiElement = file.findElementAt(offset) ?: return mutableListOf()

        if (element.elementType == UnocssTypes.CLASSNAME) {
            val result = service.resolveCssByOffset(file, offset) ?: return mutableListOf()
            return if (result.css.isNotEmpty()) {
                val target = UnocssDocumentTarget(element, result)
                mutableListOf(target)
            } else {
                mutableListOf()
            }
        }

        return mutableListOf()
    }
}

class UnocssDocumentTarget(
    private val targetElement: PsiElement?,
    private val result: ResolveCSSResult,
) :
    DocumentationTarget {
    override fun computePresentation(): TargetPresentation {
        return TargetPresentation
            .builder("Unocss Document")
            .presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = targetElement?.createSmartPointer()
        return Pointer {
            UnocssDocumentTarget(pointer?.dereference(), result)
        }
    }

    override fun computeDocumentation(): DocumentationResult {
        val cssFile: PsiFile = PsiFileFactory.getInstance(targetElement?.project)
            .createFileFromText(CSSLanguage.INSTANCE, result.css)
        val styleSheetElement = cssFile.childrenOfType<CssLazyStylesheet>()
        return DocumentationResult.Companion.asyncDocumentation {
            DocumentationResult.documentation(buildString {
                append(DocumentationMarkup.DEFINITION_START)
                runReadAction {
                    styleSheetElement.forEach { styleSheet ->
                        generateCssDoc(styleSheet)
                    }
                }
                append(DocumentationMarkup.DEFINITION_END)

                append(DocumentationMarkup.CONTENT_START)

                val colors = parseColors(result.css)
                if (colors.isNotEmpty()) {
                    val color = colors.first().toHex()
                    val style = "display: inline-block; height: 16px; width: 16px; background-color: $color"
                    append("<div style=\"$style\"></div>")
                }

                append("Generated by Unocss")
                append(DocumentationMarkup.CONTENT_END)
            })
        }
    }
}

private fun StringBuilder.generateCssDoc(element: PsiElement, indent: Int = 0) {
    val visitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            // debug the ast tree
            // val whiteSpace = " ".repeat(indent)
            // println("$whiteSpace${element.elementType} ${element.javaClass.name}")

            when (element.elementType) {
                CssElementTypes.CSS_COMMENT -> {
                    val comment = element.text
                    appendCodeSpan(
                        text = comment,
                        attr = CssHighlighter.CSS_COMMENT
                    )
                    append("<br>")
                }

                CssElementTypes.CSS_SELECTOR_LIST -> {
                    val selectors = element.childrenOfType<PsiElement>()
                    selectors.forEach { selector ->
                        appendCodeSpan(
                            text = selector.text,
                            attr = CssHighlighter.CSS_CLASS_NAME
                        )
                    }
                }

                CssElementTypes.CSS_LBRACE -> {
                    append("&nbsp;")
                    appendCodeSpan(
                        text = "{",
                        attr = CssHighlighter.CSS_BRACES
                    )
                    append("<br>")
                }

                CssElementTypes.CSS_RBRACE -> {
                    appendCodeSpan(
                        text = "}",
                        attr = CssHighlighter.CSS_BRACES
                    )
                }

                CssElementTypes.CSS_DECLARATION -> {
                    append("&nbsp;&nbsp;")
                    generateCssDoc(element, indent + 1)
                }

                CssElementTypes.CSS_IDENT -> {
                    appendCodeSpan(
                        text = element.text,
                        attr = DefaultLanguageHighlighterColors.IDENTIFIER
                    )
                }

                CssElementTypes.CSS_COLON -> {
                    appendCodeSpan(
                        text = ": ",
                        attr = DefaultLanguageHighlighterColors.OPERATION_SIGN
                    )
                }

                CssElementTypes.CSS_NUMBER -> {
                    appendCodeSpan(
                        text = element.text,
                        attr = CssHighlighter.CSS_NUMBER
                    )
                }

                CssElementTypes.CSS_FUNCTION -> {
                    generateCssDoc(element, indent + 1)
                }

                CssElementTypes.CSS_FUNCTION_TOKEN -> {
                    appendCodeSpan(
                        text = element.text,
                        attr = CssHighlighter.CSS_FUNCTION
                    )
                }

                CssElementTypes.CSS_LPAREN -> {
                    appendCodeSpan(
                        text = "(",
                        attr = CssHighlighter.CSS_BRACES
                    )
                }

                CssElementTypes.CSS_RPAREN -> {
                    appendCodeSpan(
                        text = ")",
                        attr = CssHighlighter.CSS_BRACES
                    )
                }

                CssElementTypes.CSS_COMMA -> {
                    appendCodeSpan(
                        text = ",",
                        attr = CssHighlighter.CSS_COMMA
                    )
                }

                CssElementTypes.CSS_SEMICOLON -> {
                    appendCodeSpan(
                        text = ";",
                        attr = CssHighlighter.CSS_SEMICOLON
                    )
                    append("<br/>")
                }

                CssElementTypes.CSS_STRING -> {
                    appendCodeSpan(
                        text = element.text,
                        attr = CssHighlighter.CSS_STRING
                    )
                }

                TokenType.WHITE_SPACE -> {
                    if (element.prevSibling.elementType != CssElementTypes.CSS_COMMENT) {
                        append("&nbsp;")
                    }
                }

                else -> {
                    generateCssDoc(element, indent + 1)
                }
            }
        }
    }
    element.acceptChildren(visitor)
}

private fun StringBuilder.appendCodeSpan(text: String, attr: TextAttributesKey) {
    HtmlSyntaxInfoUtil.appendStyledSpan(
        this, attr, text, DocumentationSettings.getHighlightingSaturation(true)
    )
}