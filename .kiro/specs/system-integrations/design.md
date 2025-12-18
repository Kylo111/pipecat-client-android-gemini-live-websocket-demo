# Design Document: System Integrations

## Overview

Ten dokument opisuje architekturę i projekt techniczny dla kompleksowych integracji systemowych w aplikacji Kumpel Chat. Integracje umożliwią asystentowi głosowemu Gemini Live interakcję z: kontaktami, SMS, alarmami/przypomnieniami, kalendarzem, zadaniami TODO, Google Maps, transportem publicznym oraz dedykowaną listą zakupów.

Wszystkie integracje będą:
- Konfigurowalne w UI (włączanie/wyłączanie)
- Zgodne z politykami Google Play
- Obsługiwać uprawnienia kontekstowo (przy pierwszym użyciu)

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Gemini Live API                          │
│                    (WebSocket Connection)                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ToolDefinitions.kt                         │
│              (Tool declarations for Gemini)                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       ToolExecutor.kt                           │
│                  (Tool execution router)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│  Integration  │   │  Integration  │   │  Integration  │
│   Managers    │   │   Services    │   │   Providers   │
└───────────────┘   └───────────────┘   └───────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│   Android     │   │   External    │   │    Local      │
│    System     │   │     APIs      │   │   Database    │
│  (Contacts,   │   │  (Directions  │   │  (TODO, Shop  │
│   Calendar,   │   │     API)      │   │    List)      │
│   Alarms)     │   │               │   │               │
└───────────────┘   └───────────────┘   └───────────────┘
```

### Component Architecture

```
integrations/
├── IntegrationManager.kt          # Central manager for all integrations
├── IntegrationPreferences.kt      # Preferences for enabled/disabled state
├── contacts/
│   ├── ContactsIntegration.kt     # Contacts search and lookup
│   └── SmsIntegration.kt          # SMS sending via Intent
├── alarms/
│   ├── AlarmIntegration.kt        # System alarm via ACTION_SET_ALARM
│   ├── ReminderManager.kt         # Custom reminders with AlarmManager
│   ├── ReminderReceiver.kt        # BroadcastReceiver for reminders
│   └── ReminderBootReceiver.kt    # Re-register alarms after boot
├── calendar/
│   └── CalendarIntegration.kt     # Calendar events CRUD
├── maps/
│   ├── MapsIntegration.kt         # Google Maps intents
│   └── TransitIntegration.kt      # Directions API for transit
├── notes/
│   ├── ShoppingListManager.kt     # Shopping list special note
│   ├── TodoListManager.kt         # TODO list special note
│   └── ProductCategoryMapper.kt   # Product to category mapping
└── ui/
    └── SystemIntegrationsPanel.kt # UI for integration toggles
```

## Components and Interfaces

### 1. IntegrationManager

Central manager that coordinates all integrations and checks enabled state.

```kotlin
class IntegrationManager(private val context: Context) {
    
    fun isIntegrationEnabled(integration: IntegrationType): Boolean
    fun setIntegrationEnabled(integration: IntegrationType, enabled: Boolean)
    fun getEnabledTools(): List<JsonObject>
    fun hasRequiredPermissions(integration: IntegrationType): Boolean
    
    /**
     * Request permissions using Activity Result API (ActivityResultLauncher).
     * Implementation should use registerForActivityResult() with RequestMultiplePermissions contract.
     * Deprecated onRequestPermissionsResult() flow should NOT be used.
     */
    fun getPermissionLauncher(integration: IntegrationType): ActivityResultLauncher<Array<String>>
    fun requestPermissions(integration: IntegrationType, launcher: ActivityResultLauncher<Array<String>>)
}

enum class IntegrationType {
    CONTACTS_SMS,
    ALARMS_REMINDERS,
    CALENDAR,
    TODO_LIST,
    GOOGLE_MAPS,
    PUBLIC_TRANSIT,
    SHOPPING_LIST
}
```

### 2. ContactsIntegration

Handles contact search and SMS preparation.

```kotlin
class ContactsIntegration(private val context: Context) {
    
    suspend fun searchContacts(query: String): List<Contact>
    suspend fun getContactByName(name: String): Contact?
    fun openSmsApp(phoneNumber: String, message: String)
    fun sendSmsDirect(phoneNumber: String, message: String): Result<Unit> // Enterprise only
    
    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS
        )
        val ENTERPRISE_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS
        )
    }
}

data class Contact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String?
)
```

### 3. AlarmIntegration & ReminderManager

Handles system alarms and custom reminders.

```kotlin
class AlarmIntegration(private val context: Context) {
    
    fun setSystemAlarm(hour: Int, minutes: Int, days: List<Int>?, message: String?)
    fun openAlarmApp()
}

class ReminderManager(private val context: Context) {
    
    /**
     * Creates a reminder. On Android 12+:
     * - If canScheduleExactAlarms() returns true: uses setExactAndAllowWhileIdle()
     * - If canScheduleExactAlarms() returns false: uses setAndAllowWhileIdle() as fallback
     *   (NEVER call setExact* without permission - causes SecurityException on Android 14+)
     */
    suspend fun createReminder(title: String, dateTime: LocalDateTime): Reminder
    suspend fun getReminders(): List<Reminder>
    suspend fun deleteReminder(id: Long)
    suspend fun rescheduleAllReminders() // Called after boot
    
    fun canScheduleExactAlarms(): Boolean
    fun requestExactAlarmPermission(activity: Activity)
}

data class Reminder(
    val id: Long,
    val title: String,
    val dateTime: LocalDateTime,
    val isActive: Boolean
)
```

### 4. CalendarIntegration

Handles calendar events CRUD.

```kotlin
class CalendarIntegration(private val context: Context) {
    
    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent>
    suspend fun getEventsInRange(start: LocalDate, end: LocalDate): List<CalendarEvent>
    suspend fun createEvent(event: CalendarEvent): Long?
    suspend fun updateEvent(event: CalendarEvent): Boolean
    suspend fun deleteEvent(eventId: Long): Boolean
    fun openCalendarInsert(title: String, startTime: Long, endTime: Long)
    
    companion object {
        // Rozdzielone uprawnienia - do odczytu nie potrzeba WRITE_CALENDAR
        val READ_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR
        )
        val WRITE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }
}

data class CalendarEvent(
    val id: Long? = null,
    val title: String,
    val description: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val calendarId: Long? = null
)
```

### 5. MapsIntegration & TransitIntegration

Handles Google Maps and public transit.

```kotlin
class MapsIntegration(private val context: Context) {
    
    fun startNavigation(destination: String, mode: NavigationMode)
    fun searchPlace(query: String)
    fun showLocation(lat: Double, lng: Double, label: String?)
}

enum class NavigationMode(val code: String) {
    DRIVING("d"),
    WALKING("w"),
    BICYCLING("b"),
    TWO_WHEELER("l")
}

class TransitIntegration(
    private val context: Context,
    private val directionsApiKey: String
) {
    
    /**
     * Finds transit route using Directions API.
     * 
     * @param departureTime Departure time (epoch millis). Mutually exclusive with arrivalTime.
     * @param arrivalTime Arrival time (epoch millis). Mutually exclusive with departureTime.
     * @throws IllegalArgumentException if both departureTime and arrivalTime are set
     * 
     * Note: If neither is set, API uses current time as departure.
     */
    suspend fun findTransitRoute(
        origin: LatLng,
        destination: String,
        departureTime: Long? = null,
        arrivalTime: Long? = null,
        alternatives: Boolean = false
    ): TransitResult {
        require(!(departureTime != null && arrivalTime != null)) {
            "Cannot set both departureTime and arrivalTime - Directions API accepts only one"
        }
        // ... implementation
    }
    
    /**
     * Opens Google Maps centered on location using geo: URI (display intent).
     * Note: This does NOT show the transit route from API - it's just a map view.
     * The route details are already communicated to user via voice.
     */
    fun openMapsAtLocation(lat: Double, lng: Double, label: String? = null)
    
    /**
     * Starts walking navigation to transit stop using google.navigation: intent with mode=w.
     * This is used to guide user to the departure stop before boarding transit.
     * Note: This is NOT transit navigation - transit has no turn-by-turn in Google Maps.
     */
    fun startWalkingNavigationToStop(stopLocation: LatLng, stopName: String)
}

data class TransitResult(
    val routes: List<TransitRoute>,
    val error: String? = null
)

data class TransitRoute(
    val departureStop: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val duration: Duration,
    val walkingDuration: Duration,
    val lines: List<TransitLine>
)

data class TransitLine(
    val name: String,
    val type: String, // BUS, TRAM, TRAIN, SUBWAY
    val departureStop: String,
    val arrivalStop: String
)
```

### 6. ShoppingListManager & TodoListManager

Handles special notes for shopping and TODO lists.

```kotlin
class ShoppingListManager(private val context: Context) {
    
    suspend fun getItems(): List<ShoppingItem>
    suspend fun addItem(name: String, quantity: Int? = null): ShoppingItem
    suspend fun updateItem(item: ShoppingItem)
    suspend fun deleteItem(id: Long)
    suspend fun clearPurchased()
    suspend fun clearAll()
    
    fun getCategoryForProduct(productName: String): ProductCategory
    fun saveUserCategoryCorrection(productName: String, category: ProductCategory)
}

data class ShoppingItem(
    val id: Long,
    val name: String,
    val quantity: Int?,
    val category: ProductCategory,
    val isPurchased: Boolean,
    val createdAt: LocalDateTime
)

enum class ProductCategory(val displayName: String, val order: Int) {
    DAIRY("Nabiał", 1),
    BREAD("Pieczywo", 2),
    VEGETABLES("Warzywa", 3),
    FRUITS("Owoce", 4),
    MEAT("Mięso", 5),
    FISH("Ryby", 6),
    FROZEN("Mrożonki", 7),
    DRINKS("Napoje", 8),
    SWEETS("Słodycze", 9),
    HOUSEHOLD("Chemia", 10),
    OTHER("Inne", 99)
}

class TodoListManager(private val context: Context) {
    
    suspend fun getTasks(): List<TodoTask>
    suspend fun getTasksForDate(date: LocalDate): List<TodoTask>
    suspend fun addTask(title: String, dueDate: LocalDateTime?, priority: Priority): TodoTask
    suspend fun updateTask(task: TodoTask)
    suspend fun deleteTask(id: Long)
    suspend fun clearCompleted()
}

data class TodoTask(
    val id: Long,
    val title: String,
    val dueDate: LocalDateTime?,
    val priority: Priority,
    val isCompleted: Boolean,
    val createdAt: LocalDateTime
)

enum class Priority { LOW, NORMAL, HIGH }
```

## Data Models

### Database Schema (Room)

```kotlin
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateTime: Long, // epoch millis
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Int?,
    val category: String,
    val isPurchased: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "todo_tasks")
data class TodoTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueDate: Long?, // epoch millis, nullable
    val priority: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "product_categories")
data class ProductCategoryEntity(
    @PrimaryKey val productName: String,
    val category: String,
    val isUserCorrection: Boolean = false
)
```

### Tool Definitions for Gemini

New tools to be added to ToolDefinitions.kt:

```kotlin
// Contacts & SMS
- search_contacts(query: String): List<Contact>
- send_sms(contact_name: String?, phone_number: String?, message: String)
  // Resolution: phone_number takes precedence if both provided
  // If only contact_name: lookup contact, if multiple matches return list for user to choose

// Alarms & Reminders  
- set_alarm(hour: Int, minutes: Int, days: List<String>?, label: String?)
- create_reminder(title: String, date: String, time: String)
- list_reminders(): List<Reminder>
- delete_reminder(reminder_id: Long)

// Calendar
- get_calendar_events(date: String): List<CalendarEvent>
- create_calendar_event(title: String, start: String, end: String, description: String?)
- delete_calendar_event(event_id: Long)

// TODO List
- get_todo_tasks(date: String?): List<TodoTask>
- add_todo_task(title: String, due_date: String?, priority: String?)
- complete_todo_task(task_id: Long)
- delete_todo_task(task_id: Long)

// Google Maps
- navigate_to(destination: String, mode: String)
- search_on_map(query: String)
- show_on_map(location: String)

// Public Transit
- find_transit_route(destination: String, departure_time: String?, arrival_time: String?)

// Shopping List
- get_shopping_list(): List<ShoppingItem>
- add_to_shopping_list(items: List<String>)
- remove_from_shopping_list(item_name: String)
  // If multiple items match name, return list of matches with IDs for user to choose
- remove_from_shopping_list_by_id(item_id: Long)
- mark_item_purchased(item_name: String)
  // If multiple items match name, return list of matches with IDs for user to choose
- mark_item_purchased_by_id(item_id: Long)
- clear_purchased_items()
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, the following properties have been identified. Properties have been consolidated to eliminate redundancy.

### Property 1: Contact search returns matching results
*For any* contact query string, the search function SHALL return only contacts whose display name or phone number contains the query string (case-insensitive).
**Validates: Requirements 1.1**

### Property 2: SMS Intent construction correctness
*For any* phone number and message text, the SMS Intent SHALL be constructed with correct URI scheme ("smsto:") and "sms_body" extra containing the message.
**Validates: Requirements 1.2, 1.3**

### Property 3: Permission check before sensitive operations
*For any* integration operation requiring permissions, the system SHALL check permission status before executing and request permissions if not granted.
**Validates: Requirements 1.4, 3.7, 6.6, 8.6, 9.5**

### Property 4: Alarm Intent construction correctness
*For any* alarm request with hour, minutes, and optional days, the ACTION_SET_ALARM Intent SHALL contain correct EXTRA_HOUR, EXTRA_MINUTES, and EXTRA_DAYS values.
**Validates: Requirements 2.1**

### Property 5: Reminder persistence round-trip
*For any* reminder with title and dateTime, creating the reminder and then querying reminders SHALL return a reminder with identical title and dateTime.
**Validates: Requirements 2.2, 2.3, 2.5**

### Property 6: Reminder deletion completeness
*For any* reminder that is deleted, the reminder SHALL be removed from the database AND the corresponding AlarmManager alarm SHALL be cancelled.
**Validates: Requirements 2.6**

### Property 7: Boot receiver re-registers all active reminders
*For any* set of active reminders in the database, after boot the system SHALL re-register all of them with AlarmManager.
**Validates: Requirements 2.7**

### Property 8: Exact alarm permission handling on Android 12+
*For any* reminder creation on Android 12+, if canScheduleExactAlarms() returns false, the system SHALL either request permission or use setAndAllowWhileIdle() as fallback.
**Validates: Requirements 2.8, 2.9, 2.10**

### Property 9: Calendar event persistence round-trip
*For any* calendar event with title, start time, and end time, creating the event and then querying events for that date SHALL return an event with matching properties.
**Validates: Requirements 3.1, 3.2, 3.4**

### Property 10: Calendar fallback to Intent when no permissions
*For any* calendar event creation request when WRITE_CALENDAR permission is not granted, the system SHALL construct an ACTION_INSERT Intent with correct event data.
**Validates: Requirements 3.3, 3.8**

### Property 11: TODO task persistence round-trip
*For any* TODO task with title, due date, and priority, creating the task and then querying tasks SHALL return a task with identical properties.
**Validates: Requirements 4.1, 4.2**

### Property 12: TODO task completion state change
*For any* TODO task, marking it as completed SHALL update its isCompleted flag to true in the database.
**Validates: Requirements 4.3**

### Property 13: TODO task filtering by date
*For any* date, querying tasks for that date SHALL return only tasks whose due date matches that date.
**Validates: Requirements 4.5**

### Property 14: Batch delete of completed items
*For any* list (TODO or Shopping) with some completed/purchased items, clearing completed items SHALL remove exactly those items and leave uncompleted items unchanged.
**Validates: Requirements 4.9, 7.5**

### Property 15: Google Maps navigation Intent correctness
*For any* destination and navigation mode (d/w/b/l), the google.navigation Intent SHALL be constructed with correct destination and mode parameters.
**Validates: Requirements 5.1 (driving), 5.2 (walking), 5.3 (bicycling)**

### Property 16: Google Maps search URI correctness
*For any* search query, the geo: URI SHALL be constructed with properly encoded query parameter.
**Validates: Requirements 5.4, 5.5**

### Property 17: Transit API request construction
*For any* transit route request with origin, destination, and optional departure/arrival time, the Directions API request SHALL include mode=transit and correct time parameters.
**Validates: Requirements 6.1, 6.3, 6.4, 6.5**

### Property 18: Transit response parsing completeness
*For any* valid Directions API transit response, the parsed result SHALL contain departure stop name, line number, departure time, walking duration, and total duration.
**Validates: Requirements 6.2**

### Property 19: Shopping item category assignment
*For any* product name, adding it to the shopping list SHALL assign a category from the predefined set (using dictionary lookup with fallback to OTHER).
**Validates: Requirements 7.1, 7.10**

### Property 20: Shopping list sorting by category
*For any* shopping list with items in multiple categories, retrieving the list SHALL return items sorted by category order.
**Validates: Requirements 7.2, 7.7**

### Property 21: Shopping item purchase state change
*For any* shopping item, marking it as purchased SHALL update its isPurchased flag to true in the database.
**Validates: Requirements 7.4**

### Property 22: User category correction persistence
*For any* user category correction for a product, the correction SHALL be saved and used for future additions of the same product.
**Validates: Requirements 7.11**

### Property 23: Integration toggle affects tool availability
*For any* integration type, disabling it SHALL remove its tools from the list returned by getEnabledTools(), and enabling it SHALL include its tools.
**Validates: Requirements 8.2, 8.3, 8.4**

### Property 24: Permission denial graceful degradation
*For any* permission denial, the system SHALL continue functioning with reduced capabilities without crashing.
**Validates: Requirements 9.2**

### Property 25: Permission state caching
*For any* granted permission, the system SHALL not request it again until the permission is revoked.
**Validates: Requirements 9.4**

## Error Handling

### Permission Errors
- **Missing READ_CONTACTS**: Show dialog explaining why contacts access is needed, offer to open settings
- **Missing SEND_SMS** (enterprise mode): Fall back to Intent-based SMS
- **Missing READ_CALENDAR/WRITE_CALENDAR**: Use ACTION_INSERT Intent as fallback for writes, show error for reads
- **Missing SCHEDULE_EXACT_ALARM**: Use setAndAllowWhileIdle() with warning about potential delays
- **Missing ACCESS_FINE_LOCATION**: Ask user to provide address manually

### Network Errors
- **No internet for Directions API**: Return error with clear message, suggest trying later
- **API rate limit exceeded**: Queue request and retry with exponential backoff
- **Invalid API response**: Log error, return user-friendly message

### Data Errors
- **Contact not found**: Return empty list with suggestion to check spelling
- **Calendar not available**: Suggest using Intent fallback
- **Invalid date/time format**: Parse with multiple formats, return error if all fail

### System Errors
- **AlarmManager not available**: Log error, inform user reminders may not work
- **Google Maps not installed**: Offer to open Play Store or use web fallback
- **Database error**: Retry operation, show error if persistent

## Testing Strategy

### Property-Based Testing Library
**Kotest** property testing for Kotlin (native Android testing)

### Unit Tests
- Test each integration manager in isolation with mocked Android APIs
- Test Intent construction for all navigation modes
- Test category mapping dictionary
- Test date/time parsing and formatting

### Property-Based Tests
Each correctness property will have a corresponding property-based test:

1. **Contact search** - Generate random contact lists and queries, verify search results
2. **SMS Intent** - Generate random phone numbers and messages, verify Intent structure
3. **Alarm Intent** - Generate random times and days, verify Intent extras
4. **Reminder round-trip** - Generate random reminders, verify persistence
5. **Calendar round-trip** - Generate random events, verify persistence
6. **TODO round-trip** - Generate random tasks, verify persistence
7. **Category assignment** - Generate random product names, verify category assignment
8. **Shopping list sorting** - Generate random items, verify sort order
9. **Integration toggle** - Generate random toggle states, verify tool availability

### Integration Tests
- Test full flow from Gemini tool call to system action
- Test permission request flows
- Test error handling paths
- Test boot receiver reminder restoration

### Manual Testing Scenarios
- Create reminder, reboot device, verify notification fires
- Add items to shopping list via voice, verify categorization
- Search contacts, send SMS, verify message in SMS app
- Create calendar event, verify in Google Calendar
- Request transit directions, verify route information
