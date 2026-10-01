package pl.nju.opencode.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import pl.nju.opencode.BuildConfig

/** A cached account snapshot plus when it was written. */
data class CachedAccount(
    val info: AccountInfo,
    val savedAt: Long
)

/**
 * Local cache of the last known account state.
 *
 * Lets the dashboard render real numbers instantly on a cold start instead of
 * showing skeletons while the site loads. The caller is expected to refresh
 * over the top and overwrite this once fresh data lands.
 *
 * The session cookie is separate — it lives in WebView's cookie store. This
 * cache is only the display payload.
 */
object AccountCache {

    private const val TAG = "NjuCache"
    private const val FILE = "nju_account_cache"
    private const val KEY_JSON = "account"
    private const val KEY_SAVED_AT = "saved_at"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, info: AccountInfo) {
        if (info.isUninitialized) {
            // Never clobber good cached data with an empty scrape result.
            Log.d(TAG, "skip save: nothing to store")
            return
        }
        // ...and never let a *partial* snapshot drop fields the previous one
        // had. Scrapes land out of order: the login hit has no allowance, an
        // early account hit has no data panel yet.
        val toStore = load(context)?.info?.mergeWith(info) ?: info
        try {
            val o = JSONObject()
            o.put("name", toStore.name)
            o.put("phoneNumber", toStore.phoneNumber)
            o.put("offer", toStore.offer)
            o.put("balance", toStore.balance)
            o.put("dataRemaining", toStore.dataRemaining)
            o.put("dataTotal", toStore.dataTotal)
            o.put("renewalDate", toStore.renewalDate)

            prefs(context).edit()
                .putString(KEY_JSON, o.toString())
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply()
            Log.i(TAG, "saved account snapshot")
        } catch (e: Exception) {
            Log.w(TAG, "save failed: ${e.message}")
        }
    }

    fun load(context: Context): CachedAccount? {
        // The demo edition is offline by construction: it reports this fixed
        // snapshot as "already cached", so the dashboard renders straight away
        // and MainActivity never reaches the login form. Returning a saved-at
        // of "now" keeps the "Zaktualizowano" stamp looking live.
        if (BuildConfig.DEMO) {
            return CachedAccount(DemoAccount, System.currentTimeMillis())
        }
        return try {
            val p = prefs(context)
            val json = p.getString(KEY_JSON, null) ?: return null
            val o = JSONObject(json)
            val info = AccountInfo(
                name = o.optString("name"),
                phoneNumber = o.optString("phoneNumber"),
                offer = o.optString("offer"),
                balance = o.optString("balance"),
                dataRemaining = o.optString("dataRemaining"),
                dataTotal = o.optString("dataTotal"),
                renewalDate = o.optString("renewalDate")
            )
            if (info.isUninitialized) {
                clear(context)
                return null
            }
            CachedAccount(info, p.getLong(KEY_SAVED_AT, 0L))
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        Log.d(TAG, "cache cleared")
    }
}
