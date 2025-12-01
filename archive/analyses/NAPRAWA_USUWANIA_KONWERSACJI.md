# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/ or /docs/operations/ for current documentation

---

# Naprawa usuwania konwersacji offline

## Status: ✅ NAPRAWIONE

## Problem

Przy usuwaniu konwersacji offline **nie były usuwane** powiązane dane z bazy danych Room:
- Rekord konwersacji w tabeli `conversations`
- Wszystkie sesje w tabeli `sessions`
- Transkrypty (przechowywane w sesjach)
- Podsumowania (przechowywane w sesjach)

To powodowało:
- Wyciek pamięci
- Rosnący rozmiar bazy danych
- Niespójność danych

## Rozwiązanie

Zmodyfikowano metodę `OfflineConversationManager.delete()` aby:

1. **Usuwać definicję z SharedPreferences** (jak wcześniej)
2. **Usuwać konwersację z bazy Room** (nowe)
3. **Automatycznie usuwać wszystkie sesje** (dzięki CASCADE foreign key)

### Zmiany w kodzie

#### Dodano importy:

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
```

#### Dodano CoroutineScope:

```kotlin
// Coroutine scope for database operations
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

#### Zmodyfikowano metodę delete():

```kotlin
/**
 * Delete conversation and all associated data from database
 * Removes:
 * - Conversation definition from SharedPreferences
 * - Conversation record from Room database
 * - All sessions (via CASCADE foreign key)
 * - All transcripts (stored in sessions)
 * - All summaries (stored in sessions)
 */
fun delete(id: String) {
    // Prevent deletion of system conversations
    if (id == HELP_CONVERSATION_ID) {
        android.util.Log.w(TAG, "Cannot delete system conversation")
        return
    }
    
    android.util.Log.d(TAG, "Deleting conversation: $id")
    
    // 1. Remove from SharedPreferences
    val conversations = getAll().toMutableList()
    conversations.removeAll { it.id == id }
    save(conversations)
    android.util.Log.d(TAG, "Removed conversation from SharedPreferences: $id")
    
    // 2. Remove from Room Database (with CASCADE to sessions)
    scope.launch {
        try {
            val app = context.applicationContext as RTVIApplication
            val conversationRepository = app.conversationRepository
            
            // Check if conversation exists in database
            val conversation = conversationRepository.getConversation(id)
            if (conversation != null) {
                // Delete conversation (CASCADE will delete all sessions)
                conversationRepository.deleteConversation(id)
                android.util.Log.d(TAG, "✅ Deleted conversation and all sessions from database: $id")
            } else {
                android.util.Log.d(TAG, "Conversation not found in database (may not have any sessions): $id")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to delete conversation from database: $id", e)
        }
    }
}
```

## Jak działa kaskadowe usuwanie

W `SessionEntity` zdefiniowano klucz obcy z CASCADE:

```kotlin
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE  // ✅ Automatyczne usuwanie
        )
    ]
)
```

Dzięki temu:
1. Usunięcie konwersacji z tabeli `conversations`
2. Automatycznie usuwa wszystkie sesje z tabeli `sessions`
3. Wraz z transkryptami i podsumowaniami (są polami w sesji)

## Testowanie

### Scenariusz testowy:

1. Utwórz nową konwersację offline
2. Przeprowadź kilka sesji (generując transkrypty)
3. Usuń konwersację przez UI (długie przytrzymanie → Usuń)
4. Sprawdź logi:
   ```
   OfflineConvManager: Deleting conversation: [ID]
   OfflineConvManager: Removed conversation from SharedPreferences: [ID]
   OfflineConvManager: ✅ Deleted conversation and all sessions from database: [ID]
   ```

### Weryfikacja w bazie danych:

Można sprawdzić rozmiar bazy przed i po usunięciu:
- Lokalizacja: `/data/data/ai.pipecat.gemini_multimodal_websocket_demo/databases/gemini_app_database`

## Bezpieczeństwo

- **Konwersacje systemowe** (np. "❓ Pomoc") są chronione przed usunięciem
- Operacja jest **asynchroniczna** (nie blokuje UI)
- Błędy są **logowane** ale nie crashują aplikacji
- Używa **SupervisorJob** aby błąd w jednej operacji nie anulował innych

## Pliki zmodyfikowane

- `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/OfflineConversationManager.kt`

## Build i instalacja

```bash
./gradlew :gemini-multimodal-websocket-demo:assembleDebug
./gradlew :gemini-multimodal-websocket-demo:installDebug
```

## Dodatkowe uwagi

### Dokumenty (DocumentEntity)

Tabela `documents` **nie ma** powiązania z konwersacjami, więc dokumenty nie są usuwane.
To może być przedmiotem przyszłej poprawki jeśli dokumenty będą powiązane z konwersacjami.

### Offline vs LibreChat

Ta naprawa dotyczy tylko **konwersacji offline**. Konwersacje LibreChat są zarządzane przez serwer.
