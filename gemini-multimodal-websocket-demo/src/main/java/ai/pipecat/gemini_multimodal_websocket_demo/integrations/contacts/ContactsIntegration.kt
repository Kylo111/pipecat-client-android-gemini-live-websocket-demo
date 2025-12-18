package ai.pipecat.gemini_multimodal_websocket_demo.integrations.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles contact search and SMS preparation.
 * 
 * This class provides functionality to:
 * - Search contacts by query string (name or phone number)
 * - Get contact by exact/fuzzy name matching
 * - Open SMS app with pre-filled recipient and message
 */
class ContactsIntegration(private val context: Context) {
    
    companion object {
        private const val TAG = "ContactsIntegration"
        
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS
        )
        
        val ENTERPRISE_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS
        )
    }
    
    /**
     * Check if READ_CONTACTS permission is granted.
     * 
     * @return true if permission is granted, false otherwise
     */
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if SEND_SMS permission is granted (for enterprise mode).
     * 
     * @return true if permission is granted, false otherwise
     */
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Search contacts by query string (case-insensitive).
     * Searches both display name and phone numbers.
     * 
     * @param query Search query string
     * @return List of matching contacts
     * @throws SecurityException if READ_CONTACTS permission is not granted
     */
    suspend fun searchContacts(query: String): List<Contact> = withContext(Dispatchers.IO) {
        // Check permission before accessing contacts
        if (!hasContactsPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }
        
        val contacts = mutableListOf<Contact>()
        val queryLower = query.lowercase()
        
        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex) ?: continue
                    val photoUri = it.getString(photoIndex)
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    
                    if (!hasPhone) continue
                    
                    // Get phone numbers for this contact
                    val phoneNumbers = getPhoneNumbers(id)
                    
                    // Check if name or any phone number matches query
                    val nameMatches = name.lowercase().contains(queryLower)
                    val phoneMatches = phoneNumbers.any { phone -> 
                        phone.replace(Regex("[^0-9]"), "").contains(queryLower.replace(Regex("[^0-9]"), ""))
                    }
                    
                    if (nameMatches || phoneMatches) {
                        contacts.add(
                            Contact(
                                id = id,
                                displayName = name,
                                phoneNumbers = phoneNumbers,
                                photoUri = photoUri
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Found ${contacts.size} contacts matching query: $query")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for reading contacts", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts", e)
        }
        
        return@withContext contacts
    }
    
    /**
     * Get contact by name with exact or fuzzy matching.
     * Returns the first match if multiple contacts have the same name.
     * 
     * @param name Contact name to search for
     * @return Contact if found, null otherwise
     */
    suspend fun getContactByName(name: String): Contact? = withContext(Dispatchers.IO) {
        try {
            // First try exact match
            val exactMatches = searchContacts(name).filter { 
                it.displayName.equals(name, ignoreCase = true) 
            }
            
            if (exactMatches.isNotEmpty()) {
                return@withContext exactMatches.first()
            }
            
            // Then try fuzzy match (contains)
            val fuzzyMatches = searchContacts(name)
            if (fuzzyMatches.isNotEmpty()) {
                Log.d(TAG, "Found ${fuzzyMatches.size} fuzzy matches for: $name")
                return@withContext fuzzyMatches.first()
            }
            
            Log.d(TAG, "No contact found for name: $name")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact by name", e)
            return@withContext null
        }
    }
    
    /**
     * Get phone numbers for a contact ID.
     * 
     * @param contactId Contact ID
     * @return List of phone numbers
     */
    private fun getPhoneNumbers(contactId: Long): List<String> {
        val phoneNumbers = mutableListOf<String>()
        
        try {
            val phoneCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            
            phoneCursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    if (number != null) {
                        phoneNumbers.add(number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone numbers for contact $contactId", e)
        }
        
        return phoneNumbers
    }
    
    /**
     * Open SMS app with pre-filled recipient and message.
     * Uses ACTION_SENDTO intent (Google Play compliant).
     * 
     * Phone number resolution:
     * - If phoneNumber is provided, use it directly
     * - If contactName is provided, lookup contact and use first phone number
     * - phoneNumber takes precedence if both are provided
     * 
     * @param phoneNumber Recipient phone number (optional if contactName provided)
     * @param contactName Contact name to lookup (optional if phoneNumber provided)
     * @param message Message text
     * @return Result indicating success or failure with error message
     */
    suspend fun openSmsApp(
        phoneNumber: String? = null,
        contactName: String? = null,
        message: String
    ): Result<String> {
        return try {
            // Resolve phone number
            val resolvedNumber = when {
                // Phone number takes precedence
                !phoneNumber.isNullOrBlank() -> phoneNumber
                
                // Lookup contact by name
                !contactName.isNullOrBlank() -> {
                    val contact = getContactByName(contactName)
                    if (contact == null) {
                        return Result.failure(
                            IllegalArgumentException("Contact not found: $contactName")
                        )
                    }
                    if (contact.phoneNumbers.isEmpty()) {
                        return Result.failure(
                            IllegalArgumentException("Contact has no phone numbers: $contactName")
                        )
                    }
                    contact.phoneNumbers.first()
                }
                
                else -> {
                    return Result.failure(
                        IllegalArgumentException("Either phoneNumber or contactName must be provided")
                    )
                }
            }
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$resolvedNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Opened SMS app for number: $resolvedNumber")
            
            Result.success("SMS app opened with message ready to send to $resolvedNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening SMS app", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send SMS directly without UI (Enterprise/sideload only).
     * Requires SEND_SMS permission.
     * 
     * @param phoneNumber Recipient phone number
     * @param message Message text
     * @return Result indicating success or failure
     */
    fun sendSmsDirect(phoneNumber: String, message: String): Result<Unit> {
        return try {
            // This would use SmsManager for direct sending
            // Only available in enterprise/sideload builds
            // Not implemented for Google Play version
            Result.failure(UnsupportedOperationException("Direct SMS sending not available in Google Play version"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS directly", e)
            Result.failure(e)
        }
    }
}

/**
 * Data class representing a contact.
 * 
 * @property id Contact ID from ContactsContract
 * @property displayName Contact display name
 * @property phoneNumbers List of phone numbers for this contact
 * @property photoUri URI to contact photo (nullable)
 */
data class Contact(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String?
)
