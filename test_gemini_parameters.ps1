# Test Gemini Advanced Parameters
# Sprawdza czy parametry są wysyłane do API

Write-Host "=== Test Parametrów Gemini ===" -ForegroundColor Cyan
Write-Host ""

# Wyczyść logi
Write-Host "1. Czyszczenie logów..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -c

Write-Host "2. Uruchom aplikację i rozpocznij rozmowę" -ForegroundColor Yellow
Write-Host "3. Naciśnij Enter gdy połączenie zostanie nawiązane..." -ForegroundColor Yellow
Read-Host

Write-Host ""
Write-Host "=== Sprawdzanie parametrów w logach ===" -ForegroundColor Cyan
Write-Host ""

# Pobierz ostatnie 500 linii logów
$logs = adb -s EM95IBKZEYIFSO69 logcat -d -t 500

# Szukaj parametrów połączenia
$diagnosticLines = $logs | Select-String "DIAGNOSTIC.*Connection parameters" -Context 0,15

if ($diagnosticLines) {
    Write-Host "✅ Znaleziono parametry połączenia:" -ForegroundColor Green
    Write-Host ""
    $diagnosticLines | ForEach-Object { Write-Host $_.Line }
    Write-Host ""
    
    # Sprawdź każdy parametr
    $params = @{
        "temperature" = $false
        "topP" = $false
        "topK" = $false
        "maxOutputTokens" = $false
        "presencePenalty" = $false
        "frequencyPenalty" = $false
        "stopSequences" = $false
    }
    
    foreach ($param in $params.Keys) {
        $found = $logs | Select-String "- $param"
        if ($found) {
            $params[$param] = $true
            Write-Host "  ✅ $param`: " -NoNewline -ForegroundColor Green
            $value = ($found[0].Line -split ": ")[1]
            Write-Host $value -ForegroundColor White
        } else {
            Write-Host "  ❌ $param`: NIE ZNALEZIONO" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    
    # Podsumowanie
    $foundCount = ($params.Values | Where-Object { $_ -eq $true }).Count
    $totalCount = $params.Count
    
    if ($foundCount -eq $totalCount) {
        Write-Host "🎉 SUKCES: Wszystkie parametry są wysyłane ($foundCount/$totalCount)" -ForegroundColor Green
    } elseif ($foundCount -gt 0) {
        Write-Host "⚠️  CZĘŚCIOWY SUKCES: $foundCount/$totalCount parametrów wysyłanych" -ForegroundColor Yellow
    } else {
        Write-Host "❌ BŁĄD: Żaden parametr nie jest wysyłany" -ForegroundColor Red
    }
    
} else {
    Write-Host "❌ Nie znaleziono parametrów połączenia w logach" -ForegroundColor Red
    Write-Host "   Upewnij się, że:" -ForegroundColor Yellow
    Write-Host "   1. Aplikacja jest uruchomiona" -ForegroundColor Yellow
    Write-Host "   2. Rozpocząłeś rozmowę" -ForegroundColor Yellow
    Write-Host "   3. Połączenie z Gemini zostało nawiązane" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Sprawdzanie Setup Message ===" -ForegroundColor Cyan
Write-Host ""

# Szukaj setup message (JSON wysyłany do Gemini)
$setupMsg = $logs | Select-String "Setup message preview" -Context 0,20

if ($setupMsg) {
    Write-Host "✅ Znaleziono Setup Message (JSON wysyłany do Gemini):" -ForegroundColor Green
    Write-Host ""
    $setupMsg | ForEach-Object { Write-Host $_.Line -ForegroundColor Gray }
    Write-Host ""
    
    # Sprawdź czy JSON zawiera nasze parametry
    $setupJson = $setupMsg -join "`n"
    
    Write-Host "Sprawdzanie JSON:" -ForegroundColor Cyan
    
    if ($setupJson -match "top_p") {
        Write-Host "  ✅ top_p obecne w JSON" -ForegroundColor Green
    } else {
        Write-Host "  ❌ top_p BRAK w JSON" -ForegroundColor Red
    }
    
    if ($setupJson -match "top_k") {
        Write-Host "  ✅ top_k obecne w JSON" -ForegroundColor Green
    } else {
        Write-Host "  ❌ top_k BRAK w JSON" -ForegroundColor Red
    }
    
    if ($setupJson -match "presence_penalty") {
        Write-Host "  ✅ presence_penalty obecne w JSON" -ForegroundColor Green
    } else {
        Write-Host "  ❌ presence_penalty BRAK w JSON" -ForegroundColor Red
    }
    
    if ($setupJson -match "frequency_penalty") {
        Write-Host "  ✅ frequency_penalty obecne w JSON" -ForegroundColor Green
    } else {
        Write-Host "  ❌ frequency_penalty BRAK w JSON" -ForegroundColor Red
    }
    
    if ($setupJson -match "stop_sequences") {
        Write-Host "  ✅ stop_sequences obecne w JSON" -ForegroundColor Green
    } else {
        Write-Host "  ❌ stop_sequences BRAK w JSON" -ForegroundColor Red
    }
    
} else {
    Write-Host "⚠️  Nie znaleziono Setup Message w logach" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Test zakończony ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Aby zapisać pełne logi do pliku:" -ForegroundColor Yellow
Write-Host "  adb -s EM95IBKZEYIFSO69 logcat -d > gemini_params_test.log" -ForegroundColor Gray
