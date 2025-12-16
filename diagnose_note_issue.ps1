# Diagnose note creation issue
# Check if Reasoning Agent called Perplexity before creating note

Write-Host "=== DIAGNOSING NOTE CREATION ISSUE ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Clearing logcat..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -c

Write-Host ""
Write-Host "Please reproduce the issue:" -ForegroundColor Green
Write-Host "1. Start a conversation" -ForegroundColor White
Write-Host "2. Ask for 'deep search and create note'" -ForegroundColor White
Write-Host "3. Wait for note to be created" -ForegroundColor White
Write-Host "4. Press ENTER when done" -ForegroundColor White
Read-Host

Write-Host ""
Write-Host "=== COLLECTING LOGS ===" -ForegroundColor Cyan

# Save full logs
Write-Host "Saving full logs to note_issue_full.txt..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d > note_issue_full.txt

# Extract relevant sections
Write-Host "Extracting Reasoning Agent logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "ReasoningWorker|ReasoningAgentManager|OpenRouterClient" > note_issue_reasoning.txt

Write-Host "Extracting Perplexity logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "PerplexityClient|search_perplexity|SearchPerplexity" > note_issue_perplexity.txt

Write-Host "Extracting Note Service logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "NoteService|NoteEnricher|create_note|SaveNote" > note_issue_notes.txt

Write-Host "Extracting OpenRouter response..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "OpenRouter.*response|reasoning.*actions|contextInjection" > note_issue_response.txt

Write-Host ""
Write-Host "=== ANALYSIS ===" -ForegroundColor Cyan
Write-Host ""

# Check if Perplexity was called
$perplexityLogs = Get-Content note_issue_perplexity.txt -ErrorAction SilentlyContinue
if ($perplexityLogs -and ($perplexityLogs | Select-String -Pattern "Search attempt|Searching Perplexity")) {
    Write-Host "✅ Perplexity WAS called" -ForegroundColor Green
    Write-Host "   Found search attempts in logs" -ForegroundColor White
} else {
    Write-Host "❌ Perplexity was NOT called" -ForegroundColor Red
    Write-Host "   No search attempts found in logs" -ForegroundColor White
    Write-Host "   This means Reasoning Agent skipped research!" -ForegroundColor Yellow
}

Write-Host ""

# Check if note was created
$noteLogs = Get-Content note_issue_notes.txt -ErrorAction SilentlyContinue
if ($noteLogs -and ($noteLogs | Select-String -Pattern "Creating note|Note created|executeSaveNote")) {
    Write-Host "✅ Note WAS created" -ForegroundColor Green
    Write-Host "   Found note creation in logs" -ForegroundColor White
} else {
    Write-Host "⚠️  Note creation not found in logs" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== LOG FILES CREATED ===" -ForegroundColor Cyan
Write-Host "- note_issue_full.txt (complete logs)" -ForegroundColor White
Write-Host "- note_issue_reasoning.txt (Reasoning Agent)" -ForegroundColor White
Write-Host "- note_issue_perplexity.txt (Perplexity calls)" -ForegroundColor White
Write-Host "- note_issue_notes.txt (Note creation)" -ForegroundColor White
Write-Host "- note_issue_response.txt (OpenRouter responses)" -ForegroundColor White
Write-Host ""
Write-Host "Review these files to understand what happened." -ForegroundColor Green
