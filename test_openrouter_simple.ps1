# Simple OpenRouter API Test Script
# Tests if OpenRouter API key works and model responds

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "OPENROUTER API SIMPLE TEST" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get API key from Android app preferences
Write-Host "[1/5] Getting OpenRouter API key from app..." -ForegroundColor Yellow

# Try to find preferences file
$prefsFiles = adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo ls /data/data/ai.pipecat.gemini_multimodal_websocket_demo/shared_prefs/" 2>$null

if ($prefsFiles) {
    Write-Host "   Found preferences files:" -ForegroundColor Gray
    $prefsFiles -split "`n" | ForEach-Object { Write-Host "     $_" -ForegroundColor Gray }
    
    # Try to read the first XML file
    $prefsFile = ($prefsFiles -split "`n" | Where-Object { $_ -like "*.xml" } | Select-Object -First 1).Trim()
    if ($prefsFile) {
        $prefsXml = adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo cat /data/data/ai.pipecat.gemini_multimodal_websocket_demo/shared_prefs/$prefsFile"
        
        # Extract API key
        $apiKeyMatch = [regex]::Match($prefsXml, '<string name="openRouterApiKey">(.*?)</string>')
        if ($apiKeyMatch.Success) {
            $apiKey = $apiKeyMatch.Groups[1].Value
            Write-Host "   API Key found: $($apiKey.Substring(0, 10))..." -ForegroundColor Green
        }
        
        # Extract model name
        $modelMatch = [regex]::Match($prefsXml, '<string name="reasoningAgentModel">(.*?)</string>')
        if ($modelMatch.Success) {
            $model = $modelMatch.Groups[1].Value
            Write-Host "   Model: $model" -ForegroundColor Green
        }
    }
}

# If not found, ask user to input manually
if (-not $apiKey) {
    Write-Host ""
    Write-Host "   Could not read API key from app." -ForegroundColor Yellow
    Write-Host "   Please enter your OpenRouter API key:" -ForegroundColor Yellow
    $apiKey = Read-Host "   API Key"
    
    if (-not $apiKey) {
        Write-Host "   ERROR: API key is required!" -ForegroundColor Red
        exit 1
    }
}

if (-not $model) {
    Write-Host "   Please enter model name (or press Enter for default):" -ForegroundColor Yellow
    $modelInput = Read-Host "   Model [deepseek/deepseek-v3.2]"
    $model = if ($modelInput) { $modelInput } else { "deepseek/deepseek-v3.2" }
    Write-Host "   Using model: $model" -ForegroundColor Green
}

Write-Host ""

# Prepare simple test request
Write-Host "[2/5] Preparing test request..." -ForegroundColor Yellow
$testPrompt = "Say 'Hello from OpenRouter!' and nothing else."
Write-Host "   Prompt: $testPrompt" -ForegroundColor Gray

$requestBody = @{
    model = $model
    messages = @(
        @{
            role = "user"
            content = $testPrompt
        }
    )
    temperature = 0.7
    max_tokens = 50
} | ConvertTo-Json -Depth 10

Write-Host ""

# Send request to OpenRouter
Write-Host "[3/5] Sending request to OpenRouter API..." -ForegroundColor Yellow
Write-Host "   Endpoint: https://openrouter.ai/api/v1/chat/completions" -ForegroundColor Gray
Write-Host "   Model: $model" -ForegroundColor Gray
Write-Host "   Waiting for response..." -ForegroundColor Gray

$headers = @{
    "Authorization" = "Bearer $apiKey"
    "Content-Type" = "application/json"
    "HTTP-Referer" = "https://github.com/pipecat-ai"
    "X-Title" = "Pipecat Reasoning Agent Test"
}

try {
    $startTime = Get-Date
    $response = Invoke-RestMethod -Uri "https://openrouter.ai/api/v1/chat/completions" `
        -Method Post `
        -Headers $headers `
        -Body $requestBody `
        -TimeoutSec 60
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds

    Write-Host ""
    Write-Host "[4/5] Response received!" -ForegroundColor Green
    Write-Host "   Duration: $([math]::Round($duration, 2)) seconds" -ForegroundColor Green
    Write-Host ""

    # Extract response
    if ($response.choices -and $response.choices.Count -gt 0) {
        $content = $response.choices[0].message.content
        Write-Host "[5/5] Model Response:" -ForegroundColor Yellow
        Write-Host "   $content" -ForegroundColor White
        Write-Host ""
        
        # Show usage stats
        if ($response.usage) {
            Write-Host "Usage Stats:" -ForegroundColor Cyan
            Write-Host "   Prompt tokens: $($response.usage.prompt_tokens)" -ForegroundColor Gray
            Write-Host "   Completion tokens: $($response.usage.completion_tokens)" -ForegroundColor Gray
            Write-Host "   Total tokens: $($response.usage.total_tokens)" -ForegroundColor Gray
        }
        
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "TEST PASSED - OpenRouter API works!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        
    } else {
        Write-Host "[5/5] ERROR: No response content!" -ForegroundColor Red
        Write-Host "   Full response: $($response | ConvertTo-Json -Depth 10)" -ForegroundColor Red
        exit 1
    }

} catch {
    Write-Host ""
    Write-Host "[4/5] ERROR: Request failed!" -ForegroundColor Red
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "   HTTP Status: $statusCode" -ForegroundColor Red
        
        if ($statusCode -eq 401) {
            Write-Host "   Reason: Invalid API key" -ForegroundColor Red
        } elseif ($statusCode -eq 402) {
            Write-Host "   Reason: Insufficient credits" -ForegroundColor Red
        } elseif ($statusCode -eq 404) {
            Write-Host "   Reason: Model not found" -ForegroundColor Red
        } elseif ($statusCode -eq 429) {
            Write-Host "   Reason: Rate limit exceeded" -ForegroundColor Red
        } elseif ($statusCode -ge 500) {
            Write-Host "   Reason: OpenRouter server error" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "TEST FAILED" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}
