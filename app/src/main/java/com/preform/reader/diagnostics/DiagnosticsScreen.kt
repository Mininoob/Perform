package com.preform.reader.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.reader.foldable.FoldPosture
import com.preform.reader.foldable.PostureSnapshot

@Composable
fun DiagnosticsScreen(
    snapshot: PostureSnapshot?,
    displayReport: List<String>,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Diagnostics",
                color = Color(0xFFE8E8EA),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "close",
                color = Color(0xFF8A8A90),
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }

        Text("DISPLAYS", color = Color(0xFF8A8A90), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (displayReport.isEmpty()) {
            Text("none reported", color = BAD, fontSize = 12.sp)
        }
        displayReport.forEach {
            Text(it, color = Color(0xFFB9B9C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))

        if (snapshot == null) {
            Text("waiting for WindowLayoutInfo", color = Color(0xFF8A8A90), fontSize = 13.sp)
            return@Column
        }

        val posture = when (val p = snapshot.posture) {
            is FoldPosture.Closed -> "CLOSED (cover screen, display ${p.displayId})"
            is FoldPosture.HalfOpen -> "HALF-OPEN ${p.orientation} separating=${p.isSeparating}"
            is FoldPosture.Open -> "OPEN (inner, display ${p.displayId})"
        }
        Field("posture", posture)
        Field("window px", "${snapshot.widthPx} x ${snapshot.heightPx}")
        Field("raw emissions", snapshot.rawEmissions.toString())
        Field("meaningful updates", snapshot.meaningfulUpdates.toString(), OK)

        val healthy = snapshot.createCount == 1
        Field("onCreate count", snapshot.createCount.toString(), if (healthy) OK else BAD)
        snapshot.describedFeatures.forEach {
            Text(it, color = Color(0xFFB9B9C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Field(label: String, value: String, valueColor: Color = Color(0xFFE8E8EA)) {
    Column {
        Text(label.uppercase(), color = Color(0xFF6E6E76), fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

private val OK = Color(0xFF6ED08A)
private val BAD = Color(0xFFE5675F)
