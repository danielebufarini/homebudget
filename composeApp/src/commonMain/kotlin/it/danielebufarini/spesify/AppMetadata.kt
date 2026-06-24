package it.danielebufarini.spesify

import androidx.compose.runtime.Composable

data class AppMetadata(
    val appName: String,
    val version: String,
    val buildDate: String
)

@Composable
expect fun rememberAppMetadata(): AppMetadata
