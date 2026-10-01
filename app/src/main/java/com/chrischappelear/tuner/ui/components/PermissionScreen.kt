package com.chrischappelear.tuner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrischappelear.tuner.R
import com.chrischappelear.tuner.ui.theme.TunerTheme
import com.chrischappelear.tuner.ui.theme.TunerType

@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TunerTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.tuner_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.permission_title),
            style = TunerType.cents.copy(fontSize = 28.sp),
            color = colors.ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (permanentlyDenied) R.string.permission_denied_body else R.string.permission_body),
            style = TunerType.detail.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = colors.inkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = colors.inTune, contentColor = colors.background)
        ) {
            Text(stringResource(if (permanentlyDenied) R.string.open_settings else R.string.grant_permission))
        }
    }
}
