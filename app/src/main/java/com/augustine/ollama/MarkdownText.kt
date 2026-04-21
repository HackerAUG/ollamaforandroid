package com.augustine.ollama

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    fontResource: Int? = null,
    onLinkClicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val selectionColors = LocalTextSelectionColors.current

    // We create the Markwon instance with the ImagesPlugin included
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create(context)) // This connects your ImagesPlugin
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            createTextView(ctx, fontResource, selectionColors)
        },
        update = { textView ->
            // 1. Apply the Markdown content
            markwon.setMarkdown(textView, markdown)

            // 2. Apply styling using our Extension Functions from TextExtensions.kt
            textView.applyTextColor(style.color)
            textView.applyFontSize(style.fontSize)
            textView.applyLineHeight(style.lineHeight)
            textView.applyTextDecoration(style.textDecoration)
            textView.applyTextAlign(textAlign)
            textView.applyFontFamily(style.fontFamily)
            textView.applyFontStyle(style.fontStyle)
            textView.applyFontWeight(style.fontWeight)

            // 3. Set constraints
            textView.maxLines = maxLines

            // 4. Handle links
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
    )
}

private fun createTextView(
    context: Context,
    fontResource: Int?,
    selectionColors: TextSelectionColors
): TextView {
    return TextView(context).apply {
        id = View.generateViewId()
        setTextIsSelectable(true)

        // Ensure this function exists in your TextExtensions.kt
        fontResource?.let { applyFontResource(it) }

        highlightColor = selectionColors.backgroundColor.toArgb()
        movementMethod = LinkMovementMethod.getInstance()
    }
}