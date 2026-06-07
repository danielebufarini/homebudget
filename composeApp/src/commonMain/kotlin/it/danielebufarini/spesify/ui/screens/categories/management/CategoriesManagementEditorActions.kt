package it.danielebufarini.spesify.ui.screens.categories.management

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CategoryEditorDeleteSection(
    visible: Boolean,
    isUsedCategory: Boolean,
    palette: CategoriesPalette,
    archiveCategorySheetTitle: String,
    archiveCategorySheetDescription: String,
    deleteCategorySheetTitle: String,
    deleteCategorySheetDescription: String,
    archiveLabel: String,
    deleteLabel: String,
    onDelete: () -> Unit,
) {
    if (!visible) {
        return
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = DeleteRed.copy(alpha = 0.09f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = DeleteRed,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isUsedCategory) archiveCategorySheetTitle else deleteCategorySheetTitle,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isUsedCategory) archiveCategorySheetDescription else deleteCategorySheetDescription,
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                )
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = DeleteRed),
            ) {
                Text(if (isUsedCategory) archiveLabel else deleteLabel)
            }
        }
    }
}

@Composable
internal fun CategoryEditorSaveButton(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.28f),
            disabledContentColor = Color.White.copy(alpha = 0.65f),
        ),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
        )
    }
}
