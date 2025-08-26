package com.spacemishka.app.instatoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spacemishka.app.instatoolbox.ui.theme.InstaToolboxTheme
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var hasPermission by remember { mutableStateOf(false) }
            val permissionLauncher: androidx.activity.result.ActivityResultLauncher<String> =
                rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasPermission = granted
                }
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            InstaToolboxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        hasPermission = hasPermission,
                        permissionLauncher = permissionLauncher
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun NoCropImageScreenPreview() {
    InstaToolboxTheme {
        NoCropImageScreen(hasPermission = true, onBack = {})
    }
}
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    var selectedScreen by remember { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Permission required to access images.", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            ) {
                Text("Grant Permission")
            }
        }
    } else if (selectedScreen == "NoCrop") {
        NoCropImageScreen(
            modifier = modifier,
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else if (selectedScreen == "Swipeable") {
        SwipeablePhotoGeneratorScreen(
            modifier = modifier,
            hasPermission = hasPermission,
            onBack = { selectedScreen = null }
        )
    } else if (selectedScreen == "VideoCaption") {
        VideoCaptionScreen()
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "InstaToolbox",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { selectedScreen = "NoCrop" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No-Crop Image Posting")
                    }
                    Button(
                        onClick = { selectedScreen = "Swipeable" },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true
                    ) {
                        Text("Swipeable Photo Generator")
                    }
                    Button(
                        onClick = { selectedScreen = "VideoCaption" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Video Captioning")
                    }
                    Button(
                        onClick = { /* TODO: Add next feature */ },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        Text("9-Grid Feed Creator")
                    }
                    Button(
                        onClick = { /* TODO: Add next feature */ },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        Text("Video Splitter")
                    }
                }
            }
            Text(
                text = "Select a tool to get started. More features coming soon!",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Device Model: ${android.os.Build.MODEL}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
