# IU Digital Radio

Aplicación móvil nativa para Android construida **íntegramente en Jetpack Compose**, sin
un solo layout XML para vistas. Reproduce emisoras de radio por streaming, permite al
oyente tomarse una foto de perfil con la cámara del dispositivo y responde con
vibración a cada control del reproductor.

Proyecto académico de la **Institución Universitaria Digital de Antioquia** —
Evidencia de aprendizaje 3, Programación de Dispositivos Móviles.

---

## Características

| Requisito | Implementación |
|---|---|
| **RF-01** · Maquetación declarativa | `Scaffold`, `Column`, `Row`, `Card`, `LazyColumn` y `Modifier`. Cero XML de vistas. |
| **RF-02** · Perfil con cámara | `ActivityResultContracts.TakePicturePreview`; la foto se muestra recortada en círculo con `clip(CircleShape)`. |
| **RF-03** · Permisos en runtime | `CAMERA`, `VIBRATE` e `INTERNET` declarados; `CAMERA` se solicita en ejecución con `RequestPermission`. |
| **RF-04** · Estado dinámico | `mutableStateOf` + `rememberSaveable`: el estado sobrevive a rotaciones y a la muerte del proceso. |
| **RF-05** · Retroalimentación háptica | `VibratorManager` (Android 12+) o `Vibrator`; pulso corto de 40 ms en Play, Pause y Mute. |
| **RF-06** · Lista dinámica de emisoras | `LazyColumn` con `items(key = { it.id })`; al tocar un ítem cambia la emisora activa. |
| **RF-07** · Reproducción de audio | **Media3 ExoPlayer 1.3.1** con streaming real sobre HTTPS. |

## Arquitectura

La pantalla sigue el patrón de **elevación de estado** (*state hoisting*) recomendado
por Android, separada en dos funciones composables:

- **`RadioScreen`** — concentra todo lo que depende del sistema operativo: permisos,
  cámara, vibración y el ciclo de vida de ExoPlayer.
- **`RadioContent`** — recibe solo datos y lambdas, no toca el sistema. Al no tener
  estado, es previsualizable con `@Preview` sin instanciar el reproductor.

Compose es la **única fuente de verdad**: el reproductor sigue al estado mediante
`LaunchedEffect(isPlaying)` y `LaunchedEffect(isMuted)`, nunca al revés.

### Decisiones de diseño

- **La foto no se guarda como `Bitmap` en el bundle.** Se escribe en caché y solo la
  ruta viaja en `rememberSaveable`; el `Bitmap` se reconstruye con un `LaunchedEffect`.
  Así la foto sobrevive también a que el sistema destruya el proceso mientras la
  cámara está abierta, que es el caso que más se pierde en la práctica.
- **Una sola `LazyColumn` sostiene las tres secciones.** En horizontal el contenido no
  cabría en pantalla; así todo sigue siendo accesible sin anidar scrolls.
- **El audio se pausa al salir de la app.** Sin un servicio en primer plano no debe
  seguir sonando sin controles visibles.
- **ExoPlayer se libera en un `DisposableEffect`**, y los errores de red se capturan en
  un `Player.Listener`: si un stream falla, la interfaz lo informa y sigue respondiendo.

## Estructura del proyecto

```
app/src/main/
├── AndroidManifest.xml                     Permisos CAMERA, VIBRATE, INTERNET
├── java/com/iu/digitalradio/
│   ├── MainActivity.kt                     Punto de entrada; setContent + tema
│   ├── RadioScreen.kt                      Pantalla con estado + RadioContent sin estado
│   ├── Station.kt                          Modelo Station y catálogo de emisoras
│   └── ui/theme/                           Color, Theme y Type (Material 3)
└── res/values/
    ├── strings.xml                         Todos los textos de la interfaz
    └── themes.xml
```

## Requisitos de compilación

| | |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.9 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 34 |
| minSdk | 24 (Android 7.0) |

> Media3 se fija en la versión **1.3.1** a propósito: de la 1.4 en adelante exige
> `compileSdk 35`.

## Cómo compilar

```bash
git clone https://github.com/Cristian-2533/IUDigitalRadio.git
cd IUDigitalRadio
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Si Gradle no encuentra el SDK, crea un `local.properties` en la raíz (no se versiona)
apuntando a tu instalación, **con barras normales**:

```properties
sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk
```

Desde Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Permisos

| Permiso | Tipo | Para qué |
|---|---|---|
| `CAMERA` | Peligroso | Tomar la foto de perfil. Se solicita en tiempo de ejecución; si se deniega de forma permanente, un diálogo ofrece abrir los ajustes de la app. |
| `VIBRATE` | Normal | Pulso háptico al presionar Play, Pause y Mute. |
| `INTERNET` | Normal | Streaming de audio con ExoPlayer. |

La cámara se declara con `android:required="false"`, de modo que la app siga siendo
instalable en dispositivos sin cámara.

## Emisoras

El catálogo usa streams públicos de [SomaFM](https://somafm.com), que permiten la
reproducción directa y responden sobre HTTPS. SomaFM se financia con donaciones de sus
oyentes.

## Licencia

Material académico, sin licencia de distribución. Los streams de audio pertenecen a
SomaFM y se enlazan bajo sus condiciones de uso.
