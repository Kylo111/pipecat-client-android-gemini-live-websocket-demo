# Diagnoza problemu z połączeniem Gemini
# Sprawdza czy parametry powodują błąd

Write-Host "=== Diagnoza Połączenia Gemini ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Pobieranie logów z ostatnich 2 minut..." -ForegroundColor Yellow
$logs = adb -s EM95IBKZEYIFSO69 logcat -d -t 1000

Write-Host ""
Write-Host "=== 1. Sprawdzanie błędów WebSocket ===" -ForegroundColor Cyan
$wsErrors = $logs | Select-String "WebSocket.*error|WebSocket.*fail|onFailure|onError" -Context 2

if ($wsErrors) {
    Write-Host "❌ Znaleziono błędy WebSocket:" -ForegroundColor Red
    $wsErrors | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
} else {
    Write-Host "✅ Brak błędów WebSocket" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== 2. Sprawdzanie błędów Gemini ===" -ForegroundColor Cyan
$geminiErrors = $logs | Select-String "Gemini.*error|GeminiClient.*error|Parse error" -Context 2

if ($geminiErrors) {
    Write-Host "❌ Znaleziono błędy Gemini:" -ForegroundColor Red
    $geminiErrors | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
} else {
    Write-Host "✅ Brak błędów Gemini" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== 3. Sprawdzanie Setup Complete ===" -ForegroundColor Cyan
$setupComplete = $logs | Select-String "Setup complete|SetupComplete"

if ($setupComplete) {
    Write-Host "✅ Setup zakończony pomyślnie" -ForegroundColor Green
    $setupComplete | ForEach-Object { Write-Host $_.Line -ForegroundColor Gray }
} else {
    Write-Host "❌ Setup NIE został zakończony - to jest problem!" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 4. Sprawdzanie Setup Message (JSON) ===" -ForegroundColor Cyan
$setupMsg = $logs | Select-String "Setup message preview" -Context 0,30

if ($setupMsg) {
    Write-Host "📄 Setup Message wysłany do Gemini:" -ForegroundColor Yellow
    Write-Host ""
    $setupMsg | ForEach-Object { Write-Host $_.Line -ForegroundColor Gray }
    
    # Sprawdź czy zawiera problematyczne parametry
    $setupJson = $setupMsg -join "`n"
    
    Write-Host ""
    Write-Host "Analiza parametrów w JSON:" -ForegroundColor Cyan
    
    if ($setupJson -match '"top_p":\s*null') {
        Write-Host "  ⚠️  top_p = null (OK, opcjonalny)" -ForegroundColor Yellow
    } elseif ($setupJson -match '"top_p"') {
        Write-Host "  ✅ top_p obecny z wartością" -ForegroundColor Green
    }
    
    if ($setupJson -match '"top_k":\s*null') {
        Write-Host "  ⚠️  top_k = null (OK, opcjonalny)" -ForegroundColor Yellow
    } elseif ($setupJson -match '"top_k"') {
        Write-Host "  ✅ top_k obecny z wartością" -ForegroundColor Green
    }
    
    if ($setupJson -match '"presence_penalty"') {
        Write-Host "  ⚠️  presence_penalty - może nie być wspierany przez Live API!" -ForegroundColor Red
    }
    
    if ($setupJson -match '"frequency_penalty"') {
        Write-Host "  ⚠️  frequency_penalty - może nie być wspierany przez Live API!" -ForegroundColor Red
    }
    
    if ($setupJson -match '"stop_sequences"') {
        Write-Host "  ⚠️  stop_sequences - może nie być wspierany przez Live API!" -ForegroundColor Red
    }
    
} else {
    Write-Host "❌ Nie znaleziono Setup Message" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 5. Sprawdzanie odpowiedzi od Gemini ===" -ForegroundColor Cyan
$geminiResponse = $logs | Select-String "onMessage.*text|AudioData|TurnComplete|Transcript"

if ($geminiResponse) {
    Write-Host "✅ Gemini wysyła odpowiedzi:" -ForegroundColor Green
    $geminiResponse | Select-Object -First 5 | ForEach-Object { Write-Host $_.Line -ForegroundColor Gray }
} else {
    Write-Host "❌ Brak odpowiedzi od Gemini - to jest główny problem!" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 6. Sprawdzanie stanu połączenia ===" -ForegroundColor Cyan
$connected = $logs | Select-String "Connected to Gemini|WebSocket opened"

if ($connected) {
    Write-Host "✅ WebSocket połączony" -ForegroundColor Green
} else {
    Write-Host "❌ WebSocket nie połączony" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== DIAGNOZA ===" -ForegroundColor Cyan
Write-Host ""

if (-not $setupComplete) {
    Write-Host "🔴 PROBLEM: Setup nie został zakończony" -ForegroundColor Red
    Write-Host "   Możliwe przyczyny:" -ForegroundColor Yellow
    Write-Host "   1. Gemini API odrzuca request z nieobsługiwanymi parametrami" -ForegroundColor Yellow
    Write-Host "   2. Błąd w formacie JSON" -ForegroundColor Yellow
    Write-Host "   3. Problem z API key" -ForegroundColor Yellow
} elseif (-not $geminiResponse) {
    Write-Host "🔴 PROBLEM: Setup OK, ale brak odpowiedzi" -ForegroundColor Red
    Write-Host "   Możliwe przyczyny:" -ForegroundColor Yellow
    Write-Host "   1. Parametry blokują generowanie odpowiedzi" -ForegroundColor Yellow
    Write-Host "   2. maxOutputTokens za niski" -ForegroundColor Yellow
    Write-Host "   3. stop_sequences zatrzymują odpowiedź za wcześnie" -ForegroundColor Yellow
} else {
    Write-Host "✅ Wszystko wygląda OK" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== REKOMENDACJA ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Spróbuj usunąć problematyczne parametry:" -ForegroundColor Yellow
Write-Host "  - presence_penalty (może nie być wspierany)" -ForegroundColor Yellow
Write-Host "  - frequency_penalty (może nie być wspierany)" -ForegroundColor Yellow
Write-Host "  - stop_sequences (może blokować odpowiedzi)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Zostaw tylko:" -ForegroundColor Green
Write-Host "  - temperature" -ForegroundColor Green
Write-Host "  - top_p" -ForegroundColor Green
Write-Host "  - top_k" -ForegroundColor Green
Write-Host "  - max_output_tokens" -ForegroundColor Green

Write-Host ""
Write-Host "Pełne logi zapisane do: gemini_diagnosis.log" -ForegroundColor Gray
$logs | Out-File -FilePath "gemini_diagnosis.log" -Encoding UTF8
