package ir.naderinia.nsa.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(val displayName: String, val phoneNumber: String?)

/**
 * Resolves a contact picked via ActivityResultContracts.PickContact() into a
 * display name (and phone number, if available) we can store on a Reminder.
 */
fun resolveContact(context: Context, contactUri: Uri): PickedContact? {
    val resolver: ContentResolver = context.contentResolver

    resolver.query(contactUri, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null

        val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        if (idIndex < 0 || nameIndex < 0) return null

        val contactId = cursor.getString(idIndex)
        val displayName = cursor.getString(nameIndex) ?: return null

        var phoneNumber: String? = null
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { phoneCursor ->
            if (phoneCursor.moveToFirst()) {
                phoneNumber = phoneCursor.getString(0)
            }
        }

        return PickedContact(displayName, phoneNumber)
    }
    return null
}
