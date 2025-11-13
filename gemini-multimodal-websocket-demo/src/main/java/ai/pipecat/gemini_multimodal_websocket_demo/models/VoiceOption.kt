package ai.pipecat.gemini_multimodal_websocket_demo.models

data class VoiceOption(
    val name: String,
    val description: String
)

val AVAILABLE_VOICES = listOf(
    VoiceOption("Zephyr", "jasny, żywy"),
    VoiceOption("Puck", "pozytywny, przyjazny, pewny siebie"),
    VoiceOption("Charon", "głęboki, autorytatywny, informacyjny"),
    VoiceOption("Kore", "neutralny, profesjonalny, stanowczy"),
    VoiceOption("Fenrir", "ciepły, przyjazny, podekscytowany"),
    VoiceOption("Leda", "młodzieńczy"),
    VoiceOption("Orus", "stanowczy, dojrzały"),
    VoiceOption("Aoede", "swobodny, zwiewny"),
    VoiceOption("Callirrhoe", "kobiecy"),
    VoiceOption("Autonoe", ""),
    VoiceOption("Enceladus", "męski"),
    VoiceOption("Iapetus", "casualowy, przystępny"),
    VoiceOption("Umbriel", ""),
    VoiceOption("Algieba", ""),
    VoiceOption("Despina", "kobiecy"),
    VoiceOption("Erinome", "kobiecy"),
    VoiceOption("Algenib", "ciepły, pewny, kobiecy"),
    VoiceOption("Rasalgethi", "konwersacyjny, męski"),
    VoiceOption("Laomedeia", ""),
    VoiceOption("Achernar", ""),
    VoiceOption("Alnilam", ""),
    VoiceOption("Schedar", ""),
    VoiceOption("Gacrux", "gładki, autorytatywny, kobiecy"),
    VoiceOption("Pulcherrima", "entuzjastyczny, młodzieńczy, kobiecy"),
    VoiceOption("Achird", "młodzieńczy, przyjazny, kobiecy"),
    VoiceOption("Zubenelgenubi", ""),
    VoiceOption("Vindemiatrix", ""),
    VoiceOption("Sadachbia", ""),
    VoiceOption("Sadaltager", ""),
    VoiceOption("Sulafat", "")
)
