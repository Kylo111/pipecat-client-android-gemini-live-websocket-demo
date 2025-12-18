package ai.pipecat.gemini_multimodal_websocket_demo.integrations.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Handles calendar events CRUD operations.
 * 
 * This class provides functionality to:
 * - Read calendar events for a specific date or date range
 * - Create new calendar events
 * - Update existing calendar events
 * - Delete calendar events
 * - Open system calendar app as fallback when permissions not granted
 */
class CalendarIntegration(private val context: Context) {
    
    companion object {
        private const val TAG = "CalendarIntegration"
        
        // Separate permissions for read and write operations
        val READ_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR
        )
        
        val WRITE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }
    
    /**
     * Check if READ_CALENDAR permission is granted.
     * 
     * @return true if permission is granted, false otherwise
     */
    fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if WRITE_CALENDAR permission is granted.
     * 
     * @return true if permission is granted, false otherwise
     */
    fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get calendar events for a specific date.
     * 
     * @param date The date to query events for
     * @return List of calendar events on that date
     * @throws SecurityException if READ_CALENDAR permission is not granted
     */
    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            throw SecurityException("READ_CALENDAR permission not granted")
        }
        
        // Get start and end of day in millis
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return@withContext getEventsInRange(startOfDay, endOfDay)
    }
    
    /**
     * Get calendar events in a date range.
     * 
     * @param start Start date
     * @param end End date (exclusive)
     * @return List of calendar events in the range
     * @throws SecurityException if READ_CALENDAR permission is not granted
     */
    suspend fun getEventsInRange(start: LocalDate, end: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            throw SecurityException("READ_CALENDAR permission not granted")
        }
        
        val startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return@withContext getEventsInRange(startMillis, endMillis)
    }
    
    /**
     * Get calendar events in a time range (internal method using millis).
     * 
     * @param startMillis Start time in epoch millis
     * @param endMillis End time in epoch millis
     * @return List of calendar events in the range
     */
    private fun getEventsInRange(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.CALENDAR_ID
            )
            
            val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?)"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                CalendarContract.Events.DTSTART + " ASC"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descIndex = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIndex = it.getColumnIndex(CalendarContract.Events.DTEND)
                val calIdIndex = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "Untitled"
                    val description = it.getString(descIndex)
                    val dtStart = it.getLong(startIndex)
                    val dtEnd = it.getLong(endIndex)
                    val calendarId = it.getLong(calIdIndex)
                    
                    val startTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dtStart),
                        ZoneId.systemDefault()
                    )
                    val endTime = LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dtEnd),
                        ZoneId.systemDefault()
                    )
                    
                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            description = description,
                            startTime = startTime,
                            endTime = endTime,
                            calendarId = calendarId
                        )
                    )
                }
            }
            
            Log.d(TAG, "Found ${events.size} events in range")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for reading calendar", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calendar events", e)
        }
        
        return events
    }

    
    /**
     * Create a new calendar event.
     * 
     * @param event Calendar event to create
     * @return Event ID if successful, null otherwise
     * @throws SecurityException if WRITE_CALENDAR permission is not granted
     */
    suspend fun createEvent(event: CalendarEvent): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            throw SecurityException("WRITE_CALENDAR permission not granted")
        }
        
        try {
            // Get calendar ID (use provided or find default/first writable calendar)
            val calendarId = event.calendarId ?: getWritableCalendarId()
            if (calendarId == null) {
                Log.e(TAG, "No writable calendar found")
                return@withContext null
            }
            
            val startMillis = event.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = event.endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            
            if (eventId != null) {
                Log.d(TAG, "Created calendar event with ID: $eventId")
            } else {
                Log.e(TAG, "Failed to create calendar event")
            }
            
            return@withContext eventId
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for writing calendar", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar event", e)
            return@withContext null
        }
    }
    
    /**
     * Update an existing calendar event.
     * 
     * @param event Calendar event with updated data (must have valid id)
     * @return true if successful, false otherwise
     * @throws SecurityException if WRITE_CALENDAR permission is not granted
     */
    suspend fun updateEvent(event: CalendarEvent): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            throw SecurityException("WRITE_CALENDAR permission not granted")
        }
        
        if (event.id == null) {
            Log.e(TAG, "Cannot update event without ID")
            return@withContext false
        }
        
        try {
            val startMillis = event.startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = event.endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }
            
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            
            if (rowsUpdated > 0) {
                Log.d(TAG, "Updated calendar event ID: ${event.id}")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to update calendar event ID: ${event.id}")
                return@withContext false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for writing calendar", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error updating calendar event", e)
            return@withContext false
        }
    }
    
    /**
     * Delete a calendar event.
     * 
     * @param eventId Event ID to delete
     * @return true if successful, false otherwise
     * @throws SecurityException if WRITE_CALENDAR permission is not granted
     */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) {
            throw SecurityException("WRITE_CALENDAR permission not granted")
        }
        
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            
            if (rowsDeleted > 0) {
                Log.d(TAG, "Deleted calendar event ID: $eventId")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to delete calendar event ID: $eventId")
                return@withContext false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for writing calendar", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting calendar event", e)
            return@withContext false
        }
    }
    
    /**
     * Open system calendar app with pre-filled event data (Intent fallback).
     * Use when WRITE_CALENDAR permission is not granted.
     * 
     * @param title Event title
     * @param startTime Start time in epoch millis
     * @param endTime End time in epoch millis
     * @return Result indicating success or failure
     */
    fun openCalendarInsert(title: String, startTime: Long, endTime: Long): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Opened calendar app for event insertion")
            
            Result.success("Calendar app opened with event ready to create")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening calendar app", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the first writable calendar ID.
     * Prefers the default calendar, falls back to first writable calendar found.
     * 
     * @return Calendar ID or null if no writable calendar found
     */
    private fun getWritableCalendarId(): Long? {
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )
            
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            var defaultCalendarId: Long? = null
            var firstWritableId: Long? = null
            
            cursor?.use {
                val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val accessIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val isPrimary = it.getInt(primaryIndex) == 1
                    val accessLevel = it.getInt(accessIndex)
                    
                    // Check if calendar is writable
                    val isWritable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    
                    if (isWritable) {
                        if (firstWritableId == null) {
                            firstWritableId = id
                        }
                        if (isPrimary) {
                            defaultCalendarId = id
                        }
                    }
                }
            }
            
            // Prefer default calendar, fall back to first writable
            return defaultCalendarId ?: firstWritableId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting writable calendar ID", e)
            return null
        }
    }
}

/**
 * Data class representing a calendar event.
 * 
 * @property id Event ID from CalendarContract (null for new events)
 * @property title Event title
 * @property description Event description (optional)
 * @property startTime Event start time
 * @property endTime Event end time
 * @property calendarId Calendar ID (null to use default calendar)
 */
data class CalendarEvent(
    val id: Long? = null,
    val title: String,
    val description: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val calendarId: Long? = null
)
