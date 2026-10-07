package com.preform.reader.foldable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shut phone's cover: the book's cover fitted and centered over a blurred, darkened
 * copy of itself, matching [CoverView] on the outer screen of an open phone. With no
 * cover, a title card.
 */
@Composable
fun CoverFace(cover: ImageBitmap?, title: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x8C000000)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = cover,
                    contentDescription = title,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .aspectRatio(cover.width / cover.height.toFloat())
                        .shadow(18.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2B1B12), Color(0xFF7A4A2B), Color(0xFF3D2519)))),
            )
            Text(
                text = title.uppercase(),
                color = Color(0xFFF3E4D2),
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        }
        // Hairline spine along the top edge.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x66000000))
                .align(Alignment.TopCenter),
        )
    }
}
