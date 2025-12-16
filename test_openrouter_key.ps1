# Test OpenRouter API key
# Usage: .\test_openrouter_key.ps1 "your-api-key-here"

param(
    [Parameter(Mandatory=$true)]
    [string]$ApiKey
)

Write-Host "=== OPENROUTER API KEY TEST ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Testing key: $($ApiKey.Substring(0, [Math]::Min(15, $ApiKey.Length)))..." -ForegroundColor Gray
Write-Host ""

# Test with deepseek/deepseek-v3.2 (default model)
$model = "deepseek/deepseek-v3.2"
Write-Host "Testing with model: $model" -ForegroundColor Yellow

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
    Write-Host "Sending request to OpenRouter API..." -ForegroundColor Gray
    
    $response = Invoke-RestMethod -Uri "https://openrouter.ai/api/v1/chat/completions" `
        -Method Post `
        -Headers @{
            "Authorization" = "Bearer $ApiKey"
            "Content-Type" = "application/json"
            "HTTP-Referer" = "https://github.com/pipecat-ai/pipecat-client-android"
            "X-Title" = "Pipecat Android Client"
        } `
        -Body $body `
        -TimeoutSec 30 `
        -Verbose
    
    Write-Host ""
    Write-Host "✓ SUCCESS!" -ForegroundColor Green
    Write-Host "  Model: $($response.model)" -ForegroundColor Gray
    Write-Host "  Response: $($response.choices[0].message.content)" -ForegroundColor Gray
    Write-Host "  Tokens used: $($response.usage.total_tokens)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "API key is VALID and model is accessible!" -ForegroundColor Green
    
} catch {
    Write-Host ""
    Write-Host "✗ FAILED!" -ForegroundColor Red
    Write-Host "  HTTP Status: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.ErrorDetails.Message) {
        Write-Host ""
        Write-Host "  Error details:" -ForegroundColor Red
        $errorJson = $_.ErrorDetails.Message | ConvertFrom-Json
        Write-Host "  $($errorJson | ConvertTo-Json -Depth 5)" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "Possible issues:" -ForegroundColor Yellow
    Write-Host "  1. Invalid API key" -ForegroundColor Yellow
    Write-Host "  2. Model not available (deepseek/deepseek-v3.2)" -ForegroundColor Yellow
    Write-Host "  3. Insufficient credits" -ForegroundColor Yellow
    Write-Host "  4. Network/firewall issue" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== TEST COMPLETE ===" -ForegroundColor Cyan
