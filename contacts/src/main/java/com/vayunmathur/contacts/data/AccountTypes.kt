package com.vayunmathur.contacts.data

/** Account type for the app's on-device local accounts (renamable/deletable by the user). */
const val LOCAL_ACCOUNT_TYPE = "com.vayunmathur.contacts.local"

/**
 * True for accounts the app may rename/delete: its own local type, plus the
 * device-local account whose type is null/empty (e.g. contacts created by
 * another local contacts app that has no sync adapter). Synced accounts
 * (Google etc.) have a real non-empty type and are excluded.
 */
fun isLocalAccountType(type: String?): Boolean = type.isNullOrBlank() || type == LOCAL_ACCOUNT_TYPE

/**
 * True for the implicit device-local account, which the provider identifies by having
 * *neither* an account name nor a type. It cannot be renamed: the provider requires an
 * account to specify both fields or neither, so a name-only rename leaves rows it
 * rejects on the next insert.
 */
fun isDefaultLocalAccount(name: String?, type: String?): Boolean =
    name.isNullOrBlank() && type.isNullOrBlank()
