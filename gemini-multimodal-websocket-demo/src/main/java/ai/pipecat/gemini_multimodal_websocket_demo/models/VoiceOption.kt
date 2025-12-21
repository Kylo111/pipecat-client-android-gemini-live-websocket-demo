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
