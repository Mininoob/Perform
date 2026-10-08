package com.preform.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.sync.CoverCloth

/**
 * The series' cover for a book that has none: two colours from its genre's cloth, the
 * author along a rule at the head, the title set large in italic. The lettering scales
 * with the cover, so a shelf thumbnail and the Book Mode cover are the same design.
 */
@Composable
fun TypeCover(title: String, author: String?, genres: List<String>, modifier: Modifier = Modifier) {
    val cloth = CoverCloth.of(genres, title)
    val ground = Color(cloth.ground)
    val ink = Color(cloth.ink)
    BoxWithConstraints(modifier.fillMaxSize().background(ground)) {
        val width = maxWidth.value
        val scale = width / 92f
        // Never break a word: the longest word sets the largest size that fits.
        val longest = title.split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val titleSize = minOf(15f * scale, (width - 20f * scale) / (longest * 0.62f))
        Column(Modifier.fillMaxSize().padding((10 * scale).dp)) {
            author?.let {
                Text(
                    it.uppercase(),
                    color = ink.copy(alpha = 0.85f),
                    fontSize = (6.5f * scale).sp,
                    letterSpacing = (1.3f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height((5 * scale).dp))
            }
            Box(Modifier.fillMaxWidth().height((0.75f * scale).coerceAtLeast(0.75f).dp).background(ink.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            Text(
                title,
                color = ink,
                fontFamily = LiterataDisplay,
                fontStyle = FontStyle.Italic,
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 1.08f).sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((8 * scale).dp))
            Text(
                "PREFORM",
                color = ink.copy(alpha = 0.55f),
                fontSize = (5.5f * scale).sp,
                letterSpacing = (2f * scale).sp,
            )
        }
    }
}
