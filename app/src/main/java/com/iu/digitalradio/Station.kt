package com.iu.digitalradio

/**
 * Modelo de una emisora del catálogo.
 *
 * @param id identificador estable, usado como clave en la LazyColumn.
 * @param name nombre comercial de la emisora.
 * @param frequency dial que se muestra bajo el nombre.
 * @param genre género musical, usado para agrupar visualmente.
 * @param streamUrl URL del stream de audio que consume ExoPlayer.
 */
data class Station(
    val id: Int,
    val name: String,
    val frequency: String,
    val genre: String,
    val streamUrl: String
)

/**
 * Catálogo por defecto de la aplicación.
 *
 * Se usan streams públicos de SomaFM, que permiten la reproducción directa
 * y responden sobre HTTPS (no se requiere tráfico en texto plano).
 */
val defaultStations: List<Station> = listOf(
    Station(
        id = 1,
        name = "IU Digital Groove",
        frequency = "100.5 FM",
        genre = "Downtempo",
        streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3"
    ),
    Station(
        id = 2,
        name = "IU Digital Indie",
        frequency = "102.3 FM",
        genre = "Indie Pop",
        streamUrl = "https://ice1.somafm.com/indiepop-128-mp3"
    ),
    Station(
        id = 3,
        name = "IU Digital Ambiente",
        frequency = "104.7 FM",
        genre = "Ambient",
        streamUrl = "https://ice1.somafm.com/dronezone-128-mp3"
    ),
    Station(
        id = 4,
        name = "IU Digital Lounge",
        frequency = "106.1 FM",
        genre = "Chillout",
        streamUrl = "https://ice1.somafm.com/lush-128-mp3"
    ),
    Station(
        id = 5,
        name = "IU Digital Clásicos",
        frequency = "107.9 FM",
        genre = "Lounge Retro",
        streamUrl = "https://ice1.somafm.com/secretagent-128-mp3"
    )
)
