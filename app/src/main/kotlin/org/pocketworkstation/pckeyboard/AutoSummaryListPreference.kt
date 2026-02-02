package org.pocketworkstation.pckeyboard

import android.content.Context
import android.preference.ListPreference
import android.util.AttributeSet
import android.util.Log

class AutoSummaryListPreference : ListPreference {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private fun trySetSummary() {
        val entry: CharSequence? = try {
            getEntry()
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.i(TAG, "Malfunctioning ListPreference, can't get entry")
            null
        }
        if (entry != null) {
            val percent = "percent"
            summary = entry.toString().replace("%", " $percent")
        }
    }

    override fun setEntries(entries: Array<CharSequence>?) {
        super.setEntries(entries)
        trySetSummary()
    }

    override fun setEntryValues(entryValues: Array<CharSequence>?) {
        super.setEntryValues(entryValues)
        trySetSummary()
    }

    override fun setValue(value: String?) {
        super.setValue(value)
        trySetSummary()
    }

    companion object {
        private const val TAG = "HK/AutoSummaryListPreference"
    }
}
