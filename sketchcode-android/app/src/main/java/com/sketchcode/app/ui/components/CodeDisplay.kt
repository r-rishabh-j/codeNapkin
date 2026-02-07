package com.sketchcode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays code with line numbers in a scrollable view.
 * Styled to look like a VS Code editor.
 */
@Composable
fun CodeDisplay(
    code: String,
    filename: String,
    language: String,
    modifier: Modifier = Modifier
) {
    val lines = code.split("\n")
    val lineNumWidth = lines.size.toString().length

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .horizontalScroll(rememberScrollState())
    ) {
        // File header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252526))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = filename,
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = language,
                color = Color(0xFF858585),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Code lines
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            ) {
                // Line number
                Text(
                    text = (index + 1).toString().padStart(lineNumWidth),
                    color = Color(0xFF858585),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 12.dp, start = 8.dp)
                )
                // Code content
                Text(
                    text = line,
                    color = Color(0xFFD4D4D4),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        // Bottom padding for drawing space
        Spacer(modifier = Modifier.height(200.dp))
    }
}
