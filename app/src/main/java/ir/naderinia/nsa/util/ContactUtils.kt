package ir.naderinia.nsa.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class ContactInfo(
    val displayName: String,
    val phoneNumber: String?
)

fun resolveContact(
    context: Context,
    contactUri: Uri
): ContactInfo? {
    val resolver = context.contentResolver

    var displayName: String? = null

    resolver.query(
        contactUri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            displayName = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    ContactsContract.Contacts.DISPLAY_NAME
                )
            )
        }
    }

    val phoneNumber = resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactUri.lastPathSegment),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(
                cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
            )
        } else {
            null
        }
    }

    return displayName?.let {
        ContactInfo(
            displayName = it,
            phoneNumber = phoneNumber
        )
    }
}