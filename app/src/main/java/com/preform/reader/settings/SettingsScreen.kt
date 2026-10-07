package com.preform.reader.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    backSwipeEnabled: Boolean,
    onBackSwipeEnabledChange: (Boolean) -> Unit,
    backSwipeForNext: SwipeDirection,
    onBackSwipeForNextChange: (SwipeDirection) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12100E))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Settings",
                color = Color(0xFFDDD3C6),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "done",
                color = Color(0xFFD6A15C),
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            )
        }

        Text(
            text = "BACK-OF-PHONE PAGE TURNING",
            color = Color(0xFF6E665C),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "With Book Mode on and the phone open, swipe across the cover on the " +
                "back to turn the page. Directions are as you see them from the front.",
            color = Color(0xFF9C9083),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = backSwipeEnabled,
                    onValueChange = onBackSwipeEnabledChange,
                    role = Role.Switch,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Swipe the back to turn pages",
                color = Color(0xFFDDD3C6),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = backSwipeEnabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF12100E),
                    checkedTrackColor = Color(0xFFD6A15C),
                    uncheckedThumbColor = Color(0xFF6E665C),
                    uncheckedTrackColor = Color(0xFF26221E),
                    uncheckedBorderColor = Color(0xFF6E665C),
                ),
            )
        }
        Spacer(Modifier.height(4.dp))

        // Direction only means anything while back swiping is on.
        Column(
            modifier = Modifier.alpha(if (backSwipeEnabled) 1f else 0.4f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DirectionOption(
                label = "Swipe left for the next page",
                selected = backSwipeForNext == SwipeDirection.LEFT,
                enabled = backSwipeEnabled,
                onSelect = { onBackSwipeForNextChange(SwipeDirection.LEFT) },
            )
            DirectionOption(
                label = "Swipe right for the next page",
                selected = backSwipeForNext == SwipeDirection.RIGHT,
                enabled = backSwipeEnabled,
                onSelect = { onBackSwipeForNextChange(SwipeDirection.RIGHT) },
            )
        }
    }
}

@Composable
private fun DirectionOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFFD6A15C),
                unselectedColor = Color(0xFF6E665C),
                disabledSelectedColor = Color(0xFFD6A15C),
                disabledUnselectedColor = Color(0xFF6E665C),
            ),
        )
        Text(
            text = label,
            color = Color(0xFFDDD3C6),
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
