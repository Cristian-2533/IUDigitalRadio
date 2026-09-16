package com.iu.digitalradio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla principal de IU Digital Radio.
 *
 * Combina estado dinámico (reproducción, portada, contador de "me gusta")
 * con dos accesos a hardware del dispositivo: cámara (para cambiar la
 * portada de la emisora) y vibración (como confirmación háptica al dar
 * "me gusta"), ambos respaldados por su respectivo esquema de permisos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen() {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var stationName by remember { mutableStateOf("IU Digital Radio - 100.5 FM") }
    var likeCount by remember { mutableIntStateOf(0) }

    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Carga la imagen capturada cada vez que cambia la Uri de portada.
    LaunchedEffect(coverUri) {
        val uri = coverUri
        coverBitmap = if (uri != null) loadBitmapFromUri(context, uri) else null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            coverUri = pendingCameraUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            showPermissionRationale = true
        }
    }

    fun onChangeCoverClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val uri = createImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onLikeClick() {
        vibrateDevice(context)
        likeCount++
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            CoverArt(
                bitmap = coverBitmap,
                onClick = { onChangeCoverClick() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stationName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isPlaying) "Reproduciendo ahora" else "En pausa",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            FilledIconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onLikeClick() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Me gusta")
                }

                OutlinedButton(
                    onClick = { onChangeCoverClick() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Portada")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Me gusta: $likeCount",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Permiso de cámara requerido") },
            text = {
                Text(
                    "Para cambiar la portada de la emisora, IU Digital Radio necesita " +
                        "acceso a la cámara. Concede el permiso desde los ajustes de la app."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun CoverArt(bitmap: Bitmap?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Portada de la emisora",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onClick) {
                        Text("Toca para añadir portada con la cámara")
                    }
                }
            }
        }
    }
}

/** Crea un archivo temporal en caché y devuelve una Uri segura vía FileProvider. */
private fun createImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date())
    val imageFile = File(imagesDir, "COVER_$timeStamp.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

/** Decodifica un Bitmap desde una Uri de forma compatible con la versión de Android. */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}

/** Activa la vibración del dispositivo como retroalimentación háptica. */
private fun vibrateDevice(context: Context, durationMs: Long = 200) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}
