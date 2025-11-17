# Analiza usuwania konwersacji offline

## Podsumowanie

**PROBLEM ZIDENTYFIKOWANY**: Przy usuwaniu konwersacji offline **NIE SĄ USUWANE** powiązane dane z bazy danych Room (sesje, transkrypty, podsumowania).

## Szczegółowa analiza

### 1. Warstwa pamięci konwersacji

Aplikacja posiada **dwie warstwy przechowywania danych**:

#### A. SharedPreferences (OfflineConversationManager)
- Przechowuje **definicje konwersacji offline** (tytuł, prompt systemowy, ustawienia głosu)
- Lokalizacja: `SharedPreferences` z kluczem `"offline_conversations"`
- Format: JSON z listą obiektów `OfflineConversation`

#### B. Room Database (AppDatabase)
- Przechowuje **historię sesji i transkryptów**
- Tabele:
  - `conversations` - metadane konwersacji
  - `sessions` - sesje z transkryptami i podsumowaniami
  - `documents` - załączone dokumenty (obecnie bez powiązania z konwersacją)

### 2. Relacje w bazie danych

```kotlin
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE  // ✅ Kaskadowe usuwanie
        )
    ]
)
```

**Relacja kaskadowa**: Sesje mają klucz obcy do konwersacji z `onDelete = CASCADE`, co oznacza że **teoretycznie** przy usunięciu konwersacji z bazy, wszystkie powiązane sesje powinny być automatycznie usunięte.

### 3. Aktualny proces usuwania

#### Kod w `OfflineConversationManager.delete()`:

```kotlin
fun delete(id: String) {
    // Prevent deletion of system conversations
    if (id == HELP_CONVERSATION_ID) {
        android.util.Log.w("OfflineConvManager", "Cannot delete system conversation")
        return
    }
    
    val conversations = getAll().toMutableList()
    conversations.removeAll { it.id == id }
    save(conversations)
}
```

**Co się dzieje**:
1. ✅ Usuwa definicję konwersacji z SharedPreferences
2. ❌ **NIE usuwa** konwersacji z tabeli `conversations` w Room
3. ❌ **NIE usuwa** powiązanych sesji z tabeli `sessions`
4. ❌ **NIE usuwa** transkryptów i podsumowań

#### Kod w `ConversationListScreen.kt`:

```kotlin
onDelete = if (editingOfflineConversation != null) {
    {
        OfflineConversationManager.delete(editingOfflineConversation!!.id)
        offlineConversations = OfflineConversationManager.getAll()
        showOfflineDialog = false
        editingOfflineConversation = null
    }
} else null
```

**Co się dzieje**:
1. Wywołuje tylko `OfflineConversationManager.delete()`
2. Odświeża listę konwersacji z SharedPreferences
3. Zamyka dialog

### 4. Konsekwencje obecnej implementacji

#### ❌ Problemy:

1. **Wyciek pamięci**: Dane w bazie Room nigdy nie są usuwane
2. **Rosnący rozmiar bazy**: Każda usunięta konwersacja zostawia dane w bazie
3. **Niespójność danych**: Konwersacja nie istnieje w UI, ale jej historia jest w bazie
4. **Brak możliwości odzyskania**: Użytkownik nie może odzyskać historii usuniętej konwersacji
5. **Problemy z ID**: Jeśli użytkownik utworzy nową konwersację, może dostać nowe ID, ale stare dane pozostają

#### Przykładowy scenariusz:

```
1. Użytkownik tworzy konwersację "Nauka angielskiego" (ID: abc-123)
2. Prowadzi 10 sesji, generuje transkrypty i podsumowania
3. Usuwa konwersację przez UI
4. W SharedPreferences: konwersacja usunięta ✅
5. W Room Database:
   - conversations: rekord abc-123 NADAL ISTNIEJE ❌
   - sessions: 10 sesji NADAL ISTNIEJE ❌
   - Transkrypty: NADAL ISTNIEJĄ ❌
   - Podsumowania: NADAL ISTNIEJĄ ❌
```

### 5. Dokumenty (DocumentEntity)

**Dodatkowy problem**: Tabela `documents` **NIE MA** klucza obcego do konwersacji, więc:
- Dokumenty nie są powiązane z konwersacjami
- Nie można usunąć dokumentów powiązanych z konwersacją
- Dokumenty pozostają w bazie na zawsze

## Rekomendacje naprawy

### Opcja 1: Pełne usuwanie (zalecane)

Zmodyfikować `OfflineConversationManager.delete()`:

```kotlin
fun delete(id: String) {
    // Prevent deletion of system conversations
    if (id == HELP_CONVERSATION_ID) {
        android.util.Log.w("OfflineConvManager", "Cannot delete system conversation")
        return
    }
    
    // 1. Remove from SharedPreferences
    val conversations = getAll().toMutableList()
    conversations.removeAll { it.id == id }
    save(conversations)
    
    // 2. Remove from Room Database (with cascade to sessions)
    scope.launch {
        try {
            conversationRepository.deleteConversation(id)
            Log.d(TAG, "Deleted conversation and all sessions from database: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete conversation from database", e)
        }
    }
}
```

**Wymaga**:
- Dostęp do `conversationRepository` w `OfflineConversationManager`
- Przekazanie `CoroutineScope` do managera
- Przekazanie kontekstu aplikacji

### Opcja 2: Soft delete (archiwizacja)

Zamiast usuwać, oznaczać jako "archived":

```kotlin
// W ConversationEntity dodać pole:
@ColumnInfo(name = "is_archived")
val isArchived: Boolean = false

// W OfflineConversation dodać pole:
val isArchived: Boolean = false
```

**Zalety**:
- Możliwość odzyskania danych
- Historia nie jest tracona
- Użytkownik może "przywrócić" konwersację

**Wady**:
- Dane nadal zajmują miejsce
- Potrzebny mechanizm czyszczenia starych archiwów

### Opcja 3: Dialog potwierdzenia z opcjami

Zapytać użytkownika przy usuwaniu:

```
"Czy chcesz usunąć konwersację?"

[ ] Usuń tylko definicję (zachowaj historię)
[x] Usuń wszystko (definicja + historia + podsumowania)

[Anuluj] [Usuń]
```

## Priorytet naprawy

**WYSOKI** - Problem powoduje:
- Wyciek pamięci
- Rosnący rozmiar bazy danych
- Niespójność danych
- Potencjalne problemy z wydajnością przy dużej liczbie usuniętych konwersacji

## Dodatkowe uwagi

### Dokumenty (DocumentEntity)

Należy również rozważyć:
1. Dodanie powiązania dokumentów z konwersacjami
2. Usuwanie dokumentów przy usuwaniu konwersacji
3. Lub przynajmniej mechanizm czyszczenia osieroconych dokumentów

### Testy

Po implementacji naprawy należy przetestować:
1. Usunięcie konwersacji bez sesji
2. Usunięcie konwersacji z wieloma sesjami
3. Usunięcie konwersacji z podsumowaniami
4. Sprawdzenie rozmiaru bazy przed i po usunięciu
5. Weryfikacja że kaskadowe usuwanie działa poprawnie
