package ai.pipecat.gemini_multimodal_websocket_demo.models

data class VoiceOption(
    val name: String,
    val description: String
)

val AVAILABLE_VOICES = listOf(
    VoiceOption("pl-PL-AgnieszkaNeural", "żeński, polski (Polska)"),
    VoiceOption("pl-PL-ZofiaNeural", "żeński, polski (Polska)"),
    VoiceOption("pl-PL-MarekNeural", "męski, polski (Polska)"),
    VoiceOption("pl-PL-PaulinaNeural", "żeński, polski (Polska)")
)

val GEMINI_VOICES = listOf(
    VoiceOption("Puck", "Upbeat"),
    VoiceOption("Charon", "Informative"),
    VoiceOption("Kore", "Firm"),
    VoiceOption("Fenrir", "Excitable"),
    VoiceOption("Aoede", "Breezy"),
    VoiceOption("Zephyr", "Bright"),
    VoiceOption("Orus", "Firm"),
    VoiceOption("Autonoe", "Bright"),
    VoiceOption("Umbriel", "Easy-going"),
    VoiceOption("Erinome", "Clear"),
    VoiceOption("Laomedeia", "Upbeat"),
    VoiceOption("Schedar", "Even"),
    VoiceOption("Achird", "Friendly"),
    VoiceOption("Sadachbia", "Lively"),
    VoiceOption("Enceladus", "Breathy"),
    VoiceOption("Algieba", "Smooth"),
    VoiceOption("Algenib", "Gravelly"),
    VoiceOption("Achernar", "Soft"),
    VoiceOption("Gacrux", "Mature"),
    VoiceOption("Zubenelgenubi", "Casual"),
    VoiceOption("Sadaltager", "Knowledgeable"),
    VoiceOption("Leda", "Youthful"),
    VoiceOption("Callirrhoe", "Easy-going"),
    VoiceOption("Iapetus", "Clear"),
    VoiceOption("Despina", "Smooth"),
    VoiceOption("Rasalgethi", "Informative"),
    VoiceOption("Alnilam", "Firm"),
    VoiceOption("Pulcherrima", "Forward"),
    VoiceOption("Vindemiatrix", "Gentle"),
    VoiceOption("Sulafat", "Warm")
)
