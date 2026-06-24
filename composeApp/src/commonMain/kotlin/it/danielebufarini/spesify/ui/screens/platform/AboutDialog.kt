package it.danielebufarini.spesify.ui.screens.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.rememberAppMetadata
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.about
import spesify.composeapp.generated.resources.app_icon
import spesify.composeapp.generated.resources.build_date_label
import spesify.composeapp.generated.resources.close

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val appMetadata = rememberAppMetadata()
    val aboutLabel = stringResource(Res.string.about)
    val buildDateLabel = stringResource(Res.string.build_date_label)
    val closeLabel = stringResource(Res.string.close)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = aboutLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = appMetadata.appName,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Text(
                    text = appMetadata.appName,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = appMetadata.version,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "$buildDateLabel: ${appMetadata.buildDate}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onDismiss) {
                    Text(closeLabel)
                }
            }
        }
    )
}
