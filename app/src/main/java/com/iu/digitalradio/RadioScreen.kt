package com.iu.digitalradio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.iu.digitalradio.ui.theme.IUDigitalRadioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "RadioScreen"

/**
 * Pantalla principal de IU Digital Radio (componente con estado).
 *
 * Concentra todo lo que depende del sistema operativo -permisos, camara,
 * vibracion y ExoPlayer- y delega el dibujado en [RadioContent], que es una
 * funcion sin estado y por tanto previsualizable con @Preview.
 */
@Composable
fun RadioScreen(stations: List<Station> = defaultStations) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- RF-04: estado dinamico preservado ante rotaciones de pantalla ---
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var streamFailed by rememberSaveable { mutableStateOf(false) }

    val selectedStation = stations[selectedIndex.coerceIn(0, stations.lastIndex)]

    // El Bitmap se deriva de la ruta guardada: asi la foto sobrevive tanto a una
    // rotacion como a que el sistema destruya el proceso con la camara abierta.
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoPath) {
        val path = photoPath
        photo = if (path == null) null else withContext(Dispatchers.IO) { decodePhoto(path) }
    }

    // --- RF-07: reproduccion real de audio con Media3 ExoPlayer ---
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // El stream puede fallar por red; la UI sigue reaccionando igual.
                Log.e(TAG, "Error de reproduccion: " + error.errorCodeName, error)
                streamFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Carga la emisora seleccionada cada vez que cambia (RF-06).
    LaunchedEffect(selectedStation.id) {
        streamFailed = false
        player.setMediaItem(MediaItem.fromUri(selectedStation.streamUrl))
        player.prepare()
    }

    // El estado de Compose es la unica fuente de verdad; el player la sigue.
    LaunchedEffect(isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }
    LaunchedEffect(isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    // Sin servicio en primer plano, el audio no debe seguir sonando fuera de la app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- RF-02: captura con TakePicturePreview ---
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                photoPath = withContext(Dispatchers.IO) { savePhoto(context, bitmap) }
            }
        }
    }

    // --- RF-03: solicitud del permiso de camara en tiempo de ejecucion ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null) else showPermissionDialog = true
    }

    fun onTakePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) cameraLauncher.launch(null)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    RadioContent(
        stations = stations,
        selectedStation = selectedStation,
        isPlaying = isPlaying,
        isMuted = isMuted,
        streamFailed = streamFailed,
        photo = photo,
        onTakePhoto = { onTakePhoto() },
        onPlayPause = {
            // RF-05: pulsacion haptica corta en cada control del reproductor.
            vibrate(context)
            isPlaying = !isPlaying
        },
        onToggleMute = {
            vibrate(context)
            isMuted = !isMuted
        },
        onSelectStation = { station ->
            vibrate(context)
            selectedIndex = stations.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        }
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.permission_title)) },
            text = { Text(stringResource(R.string.permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    openAppSettings(context)
                }) { Text(stringResource(R.string.permission_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.permission_dismiss))
                }
            }
        )
    }
}

/**
 * Cuerpo visual de la pantalla, sin dependencias del sistema.
 *
 * RF-01: toda la maquetacion es declarativa (Column, Row, Card, LazyColumn,
 * Modifier); no se usa ningun XML de vistas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioContent(
    stations: List<Station>,
    selectedStation: Station,
    isPlaying: Boolean,
    isMuted: Boolean,
    streamFailed: Boolean,
    photo: Bitmap?,
    onTakePhoto: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectStation: (Station) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { innerPadding ->
        // Una unica LazyColumn sostiene las tres secciones: asi el contenido
        // sigue siendo accesible en horizontal, donde no cabria en pantalla.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ProfileHeader(photo = photo, onTakePhoto = onTakePhoto) }

            item {
                PlayerCard(
                    station = selectedStation,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    streamFailed = streamFailed,
                    onPlayPause = onPlayPause,
                    onToggleMute = onToggleMute
                )
            }

            item {
                Text(
                    text = stringResource(R.string.catalog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // RF-06: catalogo dinamico; la clave estable evita recomposiciones inutiles.
            items(items = stations, key = { it.id }) { station ->
                StationRow(
                    station = station,
                    isSelected = station.id == selectedStation.id,
                    isPlaying = isPlaying && station.id == selectedStation.id,
                    onClick = { onSelectStation(station) }
                )
            }
        }
    }
}

/** Seccion superior: foto circular del oyente y disparador de la camara. */
@Composable
private fun ProfileHeader(photo: Bitmap?, onTakePhoto: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onTakePhoto() },
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = stringResource(R.string.profile_photo_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.profile_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalIconButton(onClick = onTakePhoto) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = stringResource(R.string.profile_take_photo)
                )
            }
        }
    }
}

/** Seccion central: emisora en curso y controles Play/Pause y Mute. */
@Composable
private fun PlayerCard(
    station: Station,
    isPlaying: Boolean,
    isMuted: Boolean,
    streamFailed: Boolean,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit
) {
    // Indicador "EN VIVO" que late solo mientras hay reproduccion.
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "livePulse"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = station.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = station.frequency + "  -  " + station.genre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPlaying) stringResource(R.string.player_live)
                else stringResource(R.string.player_paused),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.alpha(if (isPlaying) pulse else 1f)
            )

            if (isMuted) {
                Text(
                    text = stringResource(R.string.player_muted_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (streamFailed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stream_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.player_pause)
                        else stringResource(R.string.player_play),
                        modifier = Modifier.size(38.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) stringResource(R.string.player_unmute)
                        else stringResource(R.string.player_mute),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/** Elemento del catalogo; resalta la emisora activa. */
@Composable
private fun StationRow(
    station: Station,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Radio,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = station.frequency + "  -  " + station.genre,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Text(
                    text = stringResource(R.string.catalog_selected),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Utilidades de sistema
// ---------------------------------------------------------------------------

/**
 * Guarda la miniatura devuelta por la camara en la cache y retorna su ruta.
 * Se conserva una sola foto para no acumular archivos en el dispositivo.
 */
private fun savePhoto(context: Context, bitmap: Bitmap): String? = try {
    val dir = File(context.cacheDir, "profile").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "profile_" + System.currentTimeMillis() + ".jpg")
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    file.absolutePath
} catch (e: Exception) {
    Log.e(TAG, "No se pudo guardar la foto de perfil", e)
    null
}

/** Lee desde disco la foto de perfil previamente guardada. */
private fun decodePhoto(path: String): Bitmap? = try {
    BitmapFactory.decodeFile(path)
} catch (e: Exception) {
    Log.e(TAG, "No se pudo leer la foto de perfil", e)
    null
}

/** Abre los ajustes de la app, para permisos denegados de forma permanente. */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "No se pudieron abrir los ajustes de la aplicacion", e)
    }
}

/**
 * RF-05: pulsacion haptica corta mediante VibratorManager (Android 12+)
 * o Vibrator en versiones anteriores.
 */
private fun vibrate(context: Context, durationMs: Long = 40) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (!vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

// ---------------------------------------------------------------------------
// Previsualizaciones (Paso 2.3: validar el aspecto sin compilar la app)
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Pantalla completa - en pausa")
@Composable
private fun RadioContentPreview() {
    IUDigitalRadioTheme(dynamicColor = false) {
        Surface {
            RadioContent(
                stations = defaultStations,
                selectedStation = defaultStations.first(),
                isPlaying = false,
                isMuted = false,
                streamFailed = false,
                photo = null,
                onTakePhoto = {},
                onPlayPause = {},
                onToggleMute = {},
                onSelectStation = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Pantalla completa - reproduciendo")
@Composable
private fun RadioContentPlayingPreview() {
    IUDigitalRadioTheme(dynamicColor = false) {
        Surface {
            RadioContent(
                stations = defaultStations,
                selectedStation = defaultStations[2],
                isPlaying = true,
                isMuted = true,
                streamFailed = false,
                photo = null,
                onTakePhoto = {},
                onPlayPause = {},
                onToggleMute = {},
                onSelectStation = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Cabecera de perfil")
@Composable
private fun ProfileHeaderPreview() {
    IUDigitalRadioTheme(dynamicColor = false) {
        Surface { ProfileHeader(photo = null, onTakePhoto = {}) }
    }
}

@Preview(showBackground = true, name = "Emisora del catalogo")
@Composable
private fun StationRowPreview() {
    IUDigitalRadioTheme(dynamicColor = false) {
        Surface {
            StationRow(
                station = defaultStations.first(),
                isSelected = true,
                isPlaying = true,
                onClick = {}
            )
        }
    }
}
