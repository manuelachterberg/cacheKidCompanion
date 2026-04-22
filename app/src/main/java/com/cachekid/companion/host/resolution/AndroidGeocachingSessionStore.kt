package com.cachekid.companion.host.resolution

import android.content.Context

class AndroidGeocachingSessionStore(
    context: Context,
) : GeocachingSessionStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getCookieHeader(): String? {
        return preferences.getString(KEY_COOKIE_HEADER, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCookieHeader(cookieHeader: String?) {
        val normalized = cookieHeader
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        preferences.edit()
            .putString(KEY_COOKIE_HEADER, normalized)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_COOKIE_HEADER)
            .apply()
    }

    fun hasCookieHeader(): Boolean = getCookieHeader() != null

    private companion object {
        const val PREFERENCES_NAME = "geocaching_session"
        const val KEY_COOKIE_HEADER = "cookie_header"
    }
}
