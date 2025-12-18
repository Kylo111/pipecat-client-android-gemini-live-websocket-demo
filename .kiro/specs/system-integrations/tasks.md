# Implementation Plan

## Phase 1: Foundation and Infrastructure

- [x] 1. Set up integration infrastructure





  - [x] 1.1 Create integrations package structure


    - Create `integrations/` directory with subdirectories: contacts, alarms, calendar, maps, notes, ui
    - _Requirements: 8.1_
  - [x] 1.2 Implement IntegrationManager core


    - Create IntegrationManager.kt with IntegrationType enum
    - Implement isIntegrationEnabled/setIntegrationEnabled using SharedPreferences
    - Implement getEnabledTools() that filters tools based on enabled state
    - _Requirements: 8.2, 8.3, 8.4_
  - [ ]* 1.3 Write property test for integration toggle
    - **Property 23: Integration toggle affects tool availability**
    - **Validates: Requirements 8.2, 8.3, 8.4**
  - [x] 1.4 Implement IntegrationPreferences


    - Create IntegrationPreferences.kt for storing enabled/disabled state
    - Default all integrations to enabled on first install
    - _Requirements: 8.2_
  - [x] 1.5 Add Room database entities


    - Add ReminderEntity, ShoppingItemEntity, TodoTaskEntity, ProductCategoryEntity to existing database
    - Create DAOs for each entity
    - _Requirements: 2.3, 4.2, 7.1_

- [x] 2. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: Contacts and SMS Integration


- [x] 3. Implement Contacts integration




  - [x] 3.1 Create ContactsIntegration class


    - Implement searchContacts(query) using ContactsContract
    - Implement getContactByName(name) for exact/fuzzy matching
    - Define Contact data class
    - _Requirements: 1.1_
  - [ ]* 3.2 Write property test for contact search
    - **Property 1: Contact search returns matching results**
    - **Validates: Requirements 1.1**
    - Note: Test search/filter logic in JVM unit test; ContentProvider access requires instrumented test
  - [x] 3.3 Implement SMS via Intent


    - Create openSmsApp(phoneNumber, message) using ACTION_SENDTO intent
    - Implement phone number resolution (prefer phone_number over contact_name)
    - _Requirements: 1.2, 1.3_
  - [ ]* 3.4 Write property test for SMS Intent construction
    - **Property 2: SMS Intent construction correctness**
    - **Validates: Requirements 1.2, 1.3**
  - [x] 3.5 Add permission handling for contacts



    - Implement permission check and request using Activity Result API
    - _Requirements: 1.4_
  - [ ]* 3.6 Write property test for permission check
    - **Property 3: Permission check before sensitive operations**
    - **Validates: Requirements 1.4**
  - [x] 3.7 Add Gemini tools for contacts/SMS


    - Add search_contacts and send_sms tool definitions to ToolDefinitions.kt
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 1.1, 1.2, 1.3_


- [x] 4. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: Alarms and Reminders Integration


- [x] 5. Implement Alarms integration

  - [x] 5.1 Create AlarmIntegration class


    - Implement setSystemAlarm(hour, minutes, days, message) using ACTION_SET_ALARM
    - Handle EXTRA_HOUR, EXTRA_MINUTES, EXTRA_DAYS, EXTRA_MESSAGE
    - _Requirements: 2.1_
  - [ ]* 5.2 Write property test for alarm Intent construction
    - **Property 4: Alarm Intent construction correctness**
    - **Validates: Requirements 2.1**
  - [x] 5.3 Create ReminderManager class


    - Implement createReminder(title, dateTime) with exact alarm handling
    - Check canScheduleExactAlarms() on Android 12+ (manifest permission alone is NOT enough - denied by default on Android 14+)
    - ALWAYS do check+request flow before using setExact*
    - Use setAndAllowWhileIdle() as fallback when exact alarms not permitted
    - _Requirements: 2.2, 2.8, 2.9, 2.10_
  - [ ]* 5.4 Write property test for reminder persistence
    - **Property 5: Reminder persistence round-trip**
    - **Validates: Requirements 2.2, 2.3, 2.5**
  - [x] 5.5 Create ReminderReceiver BroadcastReceiver



    - Show notification with sound when reminder fires
    - Handle reminder even when app is not running
    - NOTE: On Android 13+ notifications require POST_NOTIFICATIONS runtime permission (handled in Phase 9)
    - _Requirements: 2.4_
  - [x] 5.6 Create ReminderBootReceiver


    - Re-register all active reminders after device boot
    - Query database for active reminders and schedule with AlarmManager
    - _Requirements: 2.7_
  - [ ]* 5.7 Write property test for boot receiver
    - **Property 7: Boot receiver re-registers all active reminders**
    - **Validates: Requirements 2.7**
  - [x] 5.8 Implement reminder CRUD operations

    - Implement getReminders(), deleteReminder(id)
    - Cancel AlarmManager alarm when deleting
    - _Requirements: 2.5, 2.6_
  - [ ]* 5.9 Write property test for reminder deletion
    - **Property 6: Reminder deletion completeness**
    - **Validates: Requirements 2.6**
  - [x] 5.10 Add AndroidManifest entries


    - Register ReminderReceiver as BroadcastReceiver
    - Register ReminderBootReceiver with intent-filter for android.intent.action.BOOT_COMPLETED and android:exported="true" (required for targetSdk 31+)
    - Add SCHEDULE_EXACT_ALARM permission for Android 12+ (note: denied by default on Android 14+)
    - Add RECEIVE_BOOT_COMPLETED permission for BootReceiver
    - _Requirements: 2.4, 2.7, 2.8_
  - [x] 5.11 Add Gemini tools for alarms/reminders



    - Add set_alarm, create_reminder, list_reminders, delete_reminder tools
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 2.1, 2.2, 2.5, 2.6, 2.11_


- [x] 6. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: Calendar Integration

- [x] 7. Implement Calendar integration





  - [x] 7.1 Create CalendarIntegration class


    - Implement getEventsForDate(date) using CalendarContract
    - Implement getEventsInRange(start, end)
    - Define CalendarEvent data class
    - _Requirements: 3.1_
  - [x] 7.2 Implement calendar write operations


    - Implement createEvent(event) using CalendarContract
    - Implement calendarId selection (use default calendar or first writable calendar)
    - Implement updateEvent(event), deleteEvent(eventId)
    - _Requirements: 3.2, 3.4, 3.5, 3.6_
  - [ ]* 7.3 Write property test for calendar round-trip
    - **Property 9: Calendar event persistence round-trip**
    - **Validates: Requirements 3.1, 3.2, 3.4**
    - Note: Test data model logic in JVM; CalendarContract access requires instrumented test
  - [x] 7.4 Implement calendar Intent fallback


    - Implement openCalendarInsert(title, startTime, endTime) using ACTION_INSERT
    - Use when WRITE_CALENDAR permission not granted
    - _Requirements: 3.3, 3.8_
  - [ ]* 7.5 Write property test for calendar fallback
    - **Property 10: Calendar fallback to Intent when no permissions**
    - **Validates: Requirements 3.3, 3.8**
  - [x] 7.6 Add permission handling for calendar


    - Separate READ_PERMISSIONS and WRITE_PERMISSIONS
    - Request only needed permissions based on operation
    - _Requirements: 3.7_
  - [x] 7.7 Add Gemini tools for calendar


    - Add get_calendar_events, create_calendar_event, delete_calendar_event tools
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 3.1, 3.2, 3.5, 3.9_

- [x] 8. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: TODO List (Special Note)

- [x] 9. Implement TODO List






  - [x] 9.1 Create TodoListManager class


    - Implement getTasks(), getTasksForDate(date)
    - Implement addTask(title, dueDate, priority)
    - Define TodoTask data class and Priority enum
    - _Requirements: 4.1, 4.2, 4.5_
  - [ ]* 9.2 Write property test for TODO persistence
    - **Property 11: TODO task persistence round-trip**
    - **Validates: Requirements 4.1, 4.2**
  - [x] 9.3 Implement TODO state management

    - Implement updateTask(task) for marking complete
    - Implement deleteTask(id), clearCompleted()
    - _Requirements: 4.3, 4.4, 4.9_
  - [ ]* 9.4 Write property test for TODO completion
    - **Property 12: TODO task completion state change**
    - **Validates: Requirements 4.3**
  - [ ]* 9.5 Write property test for TODO date filtering
    - **Property 13: TODO task filtering by date**
    - **Validates: Requirements 4.5**
  - [ ]* 9.6 Write property test for batch delete
    - **Property 14: Batch delete of completed items**
    - **Validates: Requirements 4.9**
  - [x] 9.7 Integrate TODO with reminders


    - Create reminder when task has due date (optional)
    - _Requirements: 4.7_
  - [x] 9.8 Add Gemini tools for TODO


    - Add get_todo_tasks, add_todo_task, complete_todo_task, delete_todo_task tools
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_



- [x] 10. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: Google Maps Integration

- [x] 11. Implement Google Maps integration






  - [x] 11.1 Create MapsIntegration class

    - Implement startNavigation(destination, mode) using google.navigation: intent
    - Define NavigationMode enum (DRIVING, WALKING, BICYCLING, TWO_WHEELER)
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ]* 11.2 Write property test for navigation Intent
    - **Property 15: Google Maps navigation Intent correctness**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 11.3 Implement Maps search and display

    - Implement searchPlace(query) using geo: URI
    - Implement showLocation(lat, lng, label) using geo: URI
    - _Requirements: 5.4, 5.5_
  - [ ]* 11.4 Write property test for Maps URI construction
    - **Property 16: Google Maps search URI correctness**
    - **Validates: Requirements 5.4, 5.5**

  - [x] 11.5 Add Gemini tools for Maps

    - Add navigate_to, search_on_map, show_on_map tools
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_


- [x] 12. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.

## Phase 7: Public Transit Integration


- [x] 13. Implement Transit integration




  - [x] 13.1 Create TransitIntegration class


    - Implement findTransitRoute(origin, destination, departureTime, arrivalTime, alternatives)
    - Add validation: departureTime and arrivalTime are mutually exclusive
    - Define TransitResult, TransitRoute, TransitLine data classes
    - _Requirements: 6.1, 6.3, 6.4, 6.5_
  - [ ]* 13.2 Write property test for Transit API request
    - **Property 17: Transit API request construction**
    - **Validates: Requirements 6.1, 6.3, 6.4, 6.5**
  - [x] 13.3 Implement Directions API client


    - Create HTTP client for Google Directions API
    - Parse transit response into TransitRoute objects
    - Handle alternatives parameter (note: API may return single route even with alternatives=true)
    - _Requirements: 6.2, 6.8_
  - [ ]* 13.4 Write property test for response parsing
    - **Property 18: Transit response parsing completeness**
    - **Validates: Requirements 6.2**
  - [x] 13.5 Implement helper navigation methods


    - Implement openMapsAtLocation(lat, lng, label) for showing destination
    - Implement startWalkingNavigationToStop(stopLocation, stopName)
    - _Requirements: 6.9, 6.10_
  - [x] 13.6 Add location permission handling


    - Request location permission or ask for manual address input
    - _Requirements: 6.6_
  - [x] 13.7 Add network error handling


    - Handle no internet, API errors gracefully
    - _Requirements: 6.7_
  - [x] 13.8 Add Gemini tool for transit


    - Add find_transit_route tool
    - Implement tool execution with voice response formatting
    - _Requirements: 6.1, 6.2_


- [x] 14. Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.

## Phase 8: Shopping List (Special Note)

- [x] 15. Implement Shopping List




  - [x] 15.1 Create ProductCategoryMapper


    - Define ProductCategory enum with display names and sort order
    - Create dictionary mapping common products to categories
    - Implement getCategoryForProduct(name) with fallback to OTHER
    - _Requirements: 7.1, 7.10_
  - [ ]* 15.2 Write property test for category assignment
    - **Property 19: Shopping item category assignment**
    - **Validates: Requirements 7.1, 7.10**
  - [x] 15.3 Create ShoppingListManager class


    - Implement getItems() returning items sorted by category
    - Implement addItem(name, quantity) with auto-categorization
    - _Requirements: 7.1, 7.2, 7.7_
  - [ ]* 15.4 Write property test for shopping list sorting
    - **Property 20: Shopping list sorting by category**
    - **Validates: Requirements 7.2, 7.7**
  - [x] 15.5 Implement shopping item state management


    - Implement updateItem(item) for marking purchased
    - Implement deleteItem(id), clearPurchased(), clearAll()
    - Handle non-unique names with _by_id variants
    - _Requirements: 7.4, 7.5, 7.6, 7.12_
  - [ ]* 15.6 Write property test for purchase state
    - **Property 21: Shopping item purchase state change**
    - **Validates: Requirements 7.4**
  - [x] 15.7 Implement user category corrections

    - Implement saveUserCategoryCorrection(productName, category)
    - Use corrections for future additions of same product
    - _Requirements: 7.11_
  - [ ]* 15.8 Write property test for category corrections
    - **Property 22: User category correction persistence**
    - **Validates: Requirements 7.11**
  - [x] 15.9 Add Gemini tools for shopping list


    - Add get_shopping_list, add_to_shopping_list, remove_from_shopping_list tools
    - Add mark_item_purchased, clear_purchased_items tools
    - Implement tool execution in ToolExecutor.kt
    - _Requirements: 7.1, 7.4, 7.5, 7.6, 7.7_


- [x] 16. Checkpoint - Ensure all tests pass



  - Ensure all tests pass, ask the user if questions arise.

## Phase 9: UI Integration

- [x] 17. Implement Integration Settings UI







  - [x] 17.1 Create SystemIntegrationsPanel composable


    - Add section "Integracje systemowe" to IntegrationsTab
    - Create toggle for each IntegrationType
    - Show required permissions info next to each toggle
    - _Requirements: 8.1, 8.5_
  - [x] 17.2 Implement permission status indicators


    - Show green checkmark when permissions granted
    - Show warning with link to settings when denied
    - _Requirements: 8.7, 8.8_
  - [x] 17.3 Implement contextual permission requests


    - Request permissions on first use of feature
    - Use Activity Result API with proper callbacks
    - _Requirements: 8.6, 9.1, 9.5_
  - [x] 17.3.1 Handle POST_NOTIFICATIONS permission for Android 13+


    - Request POST_NOTIFICATIONS before showing first reminder notification
    - Show explanation why notifications are needed for reminders
    - _Requirements: 2.4_
  - [ ]* 17.4 Write property test for permission caching
    - **Property 25: Permission state caching**
    - **Validates: Requirements 9.4**
  - [x] 17.5 Implement graceful degradation


    - Continue functioning with reduced capabilities when permissions denied
    - Show clear messages about what's not available
    - _Requirements: 9.2_
  - [ ]* 17.6 Write property test for graceful degradation
    - **Property 24: Permission denial graceful degradation**
    - **Validates: Requirements 9.2**

- [x] 18. Implement Special Notes UI




  - [x] 18.1 Create ShoppingListScreen composable


    - Display items grouped by category with checkboxes
    - Implement "Wyczyść kupione" button
    - Add visual distinction (cart icon, highlighted color)
    - _Requirements: 7.3, 7.5, 7.8_
  - [x] 18.2 Create TodoListScreen composable



    - Display tasks with checkboxes, due dates, priorities
    - Implement "Wyczyść ukończone" button
    - Add visual distinction (checklist icon, highlighted color)
    - _Requirements: 4.6, 4.8, 4.9_
  - [x] 18.3 Integrate special notes into notes list


    - Show Shopping List and TODO List at top of notes
    - Use distinct icons and colors
    - _Requirements: 7.8, 4.8_



- [x] 19. Checkpoint - Ensure all tests pass






  - Ensure all tests pass, ask the user if questions arise.

## Phase 10: Final Integration and Testing

- [x] 20. Wire everything together





  - [x] 20.1 Update ToolDefinitions.kt


    - Add all new tool definitions
    - Filter tools based on IntegrationManager.getEnabledTools()
    - _Requirements: 8.3, 8.4_
  - [x] 20.2 Update ToolExecutor.kt


    - Route all new tools to appropriate integration managers
    - Handle errors and return user-friendly messages
    - _Requirements: All_
  - [x] 20.3 Update AndroidManifest.xml


    - Add required permissions: READ_CONTACTS, READ_CALENDAR, WRITE_CALENDAR, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED
    - NOTE: Do NOT add SEND_SMS to main manifest for Play Store build (use enterprise productFlavor only)
    - Register all BroadcastReceivers (ReminderReceiver, ReminderBootReceiver)
    - _Requirements: 1.4, 2.8, 3.7_
  - [x] 20.4 Add Directions API key configuration


    - Add API key to BuildConfig or secure storage
    - _Requirements: 6.1_


- [x] 21. Final Checkpoint - Ensure all tests pass




  - Ensure all tests pass, ask the user if questions arise.
