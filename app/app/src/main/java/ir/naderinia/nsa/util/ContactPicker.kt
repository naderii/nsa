package ir.naderinia.nsa.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class PickedContact(val displayName: String, val phoneNumber: String?)

/**
 * Resolves a phone-number entry picked via an ACTION_PICK intent targeting
 * ContactsContract.CommonDataKinds.Phone.CONTENT_URI. Deliberately reads ONLY
 * the single URI the Contacts app handed back — that URI carries a temporary
 * read grant from the picker itself, so this needs no READ_CONTACTS
 * permission at all (unlike querying the general contacts provider, which
 * would). This mirrors Cafe Bazaar's "camera/contacts without permission"
 * guideline: let the system app do the picking, only touch what it returns.
 */
fun resolveContact(context: Context, phoneDataUri: Uri): PickedContact? {
    context.contentResolver.query(
        phoneDataUri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null, null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
        if (name.isNullOrBlank()) return null
        return PickedContact(name, number)
    }
    return null
}
