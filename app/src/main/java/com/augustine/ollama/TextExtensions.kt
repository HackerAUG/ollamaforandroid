package com.augustine.ollama

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified

fun TextView.applyTextColor(color: Color) {
    this.setTextColor(color.toArgb())
}

fun TextView.applyFontSize(size: TextUnit) {
    if (!size.isUnspecified) {
        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.value)
    }
}

fun TextView.applyLineHeight(lineHeight: TextUnit) {
    // Basic implementation to avoid errors
}

fun TextView.applyTextDecoration(decoration: TextDecoration?) {
    if (decoration == TextDecoration.Underline) {
        this.paintFlags = this.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    }
}

fun TextView.applyTextAlign(align: TextAlign) {
    this.textAlignment = when (align) {
        TextAlign.Left, TextAlign.Start -> TextView.TEXT_ALIGNMENT_VIEW_START
        TextAlign.Right, TextAlign.End -> TextView.TEXT_ALIGNMENT_VIEW_END
        TextAlign.Center -> TextView.TEXT_ALIGNMENT_CENTER
        else -> TextView.TEXT_ALIGNMENT_GRAVITY
    }
}

fun TextView.applyFontFamily(family: FontFamily?) { /* Optional */ }
fun TextView.applyFontStyle(style: FontStyle?) { /* Optional */ }
fun TextView.applyFontWeight(weight: FontWeight?) { /* Optional */ }
fun TextView.applyFontResource(fontId: Int?) { /* Optional */ }

fun TextView.enableTextOverflow() {
    this.ellipsize = TextUtils.TruncateAt.END
}

fun TextView.applyTextSelectionColors(selectionColors: androidx.compose.foundation.text.selection.TextSelectionColors?) {
    selectionColors?.let {
        this.highlightColor = it.backgroundColor.toArgb()
    }
}