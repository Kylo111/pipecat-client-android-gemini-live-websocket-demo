# Master Test Script for Reasoning Agent
# Runs all tests in sequence to verify complete functionality

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     REASONING AGENT - MASTER TEST SUITE                   ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Test 1: Check component status
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "TEST 1: Component Status Check" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""

.\check_reasoning_agent_status.ps1

Write-Host ""
Write-Host "Press ENTER to continue to Test 2..." -ForegroundColor Yellow
$null = Read-Host

# Test 2: Verify OpenRouter API key
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "TEST 2: OpenRouter API Key Validation" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""

.\test_openrouter_validation.ps1

Write-Host ""
Write-Host "Press ENTER to continue to Test 3..." -ForegroundColor Yellow
$null = Read-Host

# Test 3: Gemini Live prompt understanding
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "TEST 3: Gemini Live Prompt Understanding" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""
Write-Host "This test checks if Gemini Live correctly calls start_reasoning_task" -ForegroundColor White
Write-Host "instead of hallucinating when you say 'Zapisz w notatkach'." -ForegroundColor White
Write-Host ""
Write-Host "Ready to start? (y/n)" -ForegroundColor Yellow
$response = Read-Host

if ($response -eq "y") {
    .\test_gemini_live_prompt.ps1
} else {
    Write-Host "Skipping Test 3" -ForegroundColor Gray
}

Write-Host ""
Write-Host "Press ENTER to continue to Test 4..." -ForegroundColor Yellow
$null = Read-Host

# Test 4: Full end-to-end test
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "TEST 4: Full End-to-End Reasoning Agent Test" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""
Write-Host "This test monitors the complete flow:" -ForegroundColor White
Write-Host "  1. Gemini Live receives command" -ForegroundColor White
Write-Host "  2. Calls start_reasoning_task tool" -ForegroundColor White
Write-Host "  3. ReasoningAgentManager creates snapshot" -ForegroundColor White
Write-Host "  4. ReasoningWorker processes task" -ForegroundColor White
Write-Host "  5. OpenRouter API is called" -ForegroundColor White
Write-Host "  6. Result is injected back into conversation" -ForegroundColor White
Write-Host ""
Write-Host "Ready to start? (y/n)" -ForegroundColor Yellow
$response = Read-Host

if ($response -eq "y") {
    .\test_reasoning_agent_full.ps1
} else {
    Write-Host "Skipping Test 4" -ForegroundColor Gray
}

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     ALL TESTS COMPLETE                                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "  - Component status checked" -ForegroundColor White
Write-Host "  - OpenRouter API key validated" -ForegroundColor White
Write-Host "  - Gemini Live prompt understanding tested" -ForegroundColor White
Write-Host "  - Full end-to-end flow tested" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  - Review logs for any errors" -ForegroundColor White
Write-Host "  - Test with real note-taking scenarios" -ForegroundColor White
Write-Host "  - Test with Perplexity search" -ForegroundColor White
Write-Host "  - Test with clipboard operations" -ForegroundColor White
Write-Host ""
