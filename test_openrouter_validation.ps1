# Test OpenRouter API key validation
# Usage: .\test_openrouter_validation.ps1

Write-Host "=== OPENROUTER API KEY VALIDATION TEST ===" -ForegroundColor Cyan
Write-Host ""

# Read API key from .env file
$envFile = ".env"
if (Test-Path $envFile) {
    $apiKey = (Get-Content $envFile | Where-Object { $_ -match "^OPENROUTER_API_KEY=" }) -replace "OPENROUTER_API_KEY=", ""
    if ($apiKey) {
        Write-Host "✓ Found OpenRouter API key in .env" -ForegroundColor Green
        Write-Host "  Key prefix: $($apiKey.Substring(0, [Math]::Min(15, $apiKey.Length)))..." -ForegroundColor Gray
    } else {
        Write-Host "✗ No OPENROUTER_API_KEY found in .env" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✗ .env file not found" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Testing with different models..." -ForegroundColor Cyan
Write-Host ""

# Test models
$models = @(
    "deepseek/deepseek-v3.2",
    "deepseek/deepseek-r1",
    "anthropic/claude-3.5-sonnet",
    "google/gemini-2.0-flash-exp"
)

foreach ($model in $models) {
    Write-Host "Testing model: $model" -ForegroundColor Yellow
    
    $body = @{
        model = $model
        messages = @(
            @{
                role = "user"
                content = "Hello"
            }
        )
        temperature = 0.1
        max_tokens = 10
    } | ConvertTo-Json -Depth 10
    
    try {
        $response = Invoke-RestMethod -Uri "https://openrouter.ai/api/v1/chat/completions" `
            -Method Post `
            -Headers @{
                "Authorization" = "Bearer $apiKey"
                "Content-Type" = "application/json"
                "HTTP-Referer" = "https://github.com/pipecat-ai/pipecat-client-android"
                "X-Title" = "Pipecat Android Client"
            } `
            -Body $body `
            -TimeoutSec 30
        
        Write-Host "  ✓ SUCCESS" -ForegroundColor Green
        Write-Host "  Response: $($response.choices[0].message.content)" -ForegroundColor Gray
    } catch {
        Write-Host "  ✗ FAILED" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails.Message) {
            Write-Host "  Details: $($_.ErrorDetails.Message)" -ForegroundColor Red
        }
    }
    Write-Host ""
}

Write-Host "=== TEST COMPLETE ===" -ForegroundColor Cyan
