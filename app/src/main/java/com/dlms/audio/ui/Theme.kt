package com.dlms.audio.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable fun DlmsTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(background=Color(0xFF08090B),surface=Color(0xFF111318),primary=Color(0xFF7BAAF7),secondary=Color(0xFF8DD6C7)),typography=Typography(),content=content)}
