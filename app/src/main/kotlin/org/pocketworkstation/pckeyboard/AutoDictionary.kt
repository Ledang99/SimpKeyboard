/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.pocketworkstation.pckeyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.os.AsyncTask
import android.provider.BaseColumns
import android.util.Log

/**
 * Stores new words temporarily until they are promoted to the user dictionary
 * for longevity. Words in the auto dictionary are used to determine if it's ok
 * to accept a word that's not in the main or user dictionary. Using a new word
 * repeatedly will promote it to the user dictionary.
 */
class AutoDictionary(
    context: Context,
    private val mIme: LatinIME,
    private val mLocale: String?,
    dicTypeId: Int
) : ExpandableDictionary(context, dicTypeId) {

    private var mPendingWrites = HashMap<String, Int?>()
    private val mPendingWritesLock = Any()

    init {
        if (sOpenHelper == null) {
            sOpenHelper = DatabaseHelper(getContext())
        }
        if (mLocale != null && mLocale.length > 1) {
            loadDictionary()
        }
    }

    override fun isValidWord(word: CharSequence): Boolean {
        val frequency = getWordFrequency(word)
        return frequency >= VALIDITY_THRESHOLD
    }

    override fun close() {
        flushPendingWrites()
        super.close()
    }

    override fun loadDictionaryAsync() {
        val cursor = query(COLUMN_LOCALE + "=?", arrayOf(mLocale ?: ""))
        try {
            if (cursor.moveToFirst()) {
                val wordIndex = cursor.getColumnIndex(COLUMN_WORD)
                val frequencyIndex = cursor.getColumnIndex(COLUMN_FREQUENCY)
                while (!cursor.isAfterLast) {
                    val word = cursor.getString(wordIndex)
                    val frequency = cursor.getInt(frequencyIndex)
                    if (word.length < getMaxWordLength()) {
                        super.addWord(word, frequency)
                    }
                    cursor.moveToNext()
                }
            }
        } finally {
            cursor.close()
        }
    }

    override fun addWord(word: String, frequency: Int) {
        var processedWord = word
        val length = processedWord.length
        if (length < 2 || length > getMaxWordLength()) return
        if (mIme.currentWord.isAutoCapitalized) {
            processedWord = Character.toLowerCase(processedWord[0]) + processedWord.substring(1)
        }
        var freq = getWordFrequency(processedWord)
        freq = if (freq < 0) frequency else freq + frequency
        super.addWord(processedWord, freq)

        if (freq >= PROMOTION_THRESHOLD) {
            mIme.promoteToUserDictionary(processedWord, FREQUENCY_FOR_AUTO_ADD)
            freq = 0
        }

        synchronized(mPendingWritesLock) {
            mPendingWrites[processedWord] = if (freq == 0) null else freq
        }
    }

    /**
     * Schedules a background thread to write any pending words to the database.
     */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            if (mPendingWrites.isEmpty()) return
            UpdateDbTask(getContext(), sOpenHelper!!, mPendingWrites, mLocale ?: "").execute()
            mPendingWrites = HashMap()
        }
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $AUTODICT_TABLE_NAME (" +
                        "$COLUMN_ID INTEGER PRIMARY KEY," +
                        "$COLUMN_WORD TEXT," +
                        "$COLUMN_FREQUENCY INTEGER," +
                        "$COLUMN_LOCALE TEXT" +
                        ");"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w(
                "AutoDictionary", "Upgrading database from version $oldVersion to " +
                        "$newVersion, which will destroy all old data"
            )
            db.execSQL("DROP TABLE IF EXISTS $AUTODICT_TABLE_NAME")
            onCreate(db)
        }
    }

    private fun query(selection: String, selectionArgs: Array<String>) = run {
        val qb = SQLiteQueryBuilder()
        qb.tables = AUTODICT_TABLE_NAME
        qb.projectionMap = sDictProjectionMap
        val db = sOpenHelper!!.readableDatabase
        qb.query(db, null, selection, selectionArgs, null, null, DEFAULT_SORT_ORDER)
    }

    private class UpdateDbTask(
        context: Context,
        private val mDbHelper: DatabaseHelper,
        private val mMap: HashMap<String, Int?>,
        private val mLocale: String
    ) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg v: Void): Void? {
            val db = mDbHelper.writableDatabase
            for ((key, freq) in mMap.entries) {
                db.delete(
                    AUTODICT_TABLE_NAME, "$COLUMN_WORD=? AND $COLUMN_LOCALE=?",
                    arrayOf(key, mLocale)
                )
                if (freq != null) {
                    db.insert(AUTODICT_TABLE_NAME, null, getContentValues(key, freq, mLocale))
                }
            }
            return null
        }

        private fun getContentValues(word: String, frequency: Int, locale: String): ContentValues {
            val values = ContentValues(4)
            values.put(COLUMN_WORD, word)
            values.put(COLUMN_FREQUENCY, frequency)
            values.put(COLUMN_LOCALE, locale)
            return values
        }
    }

    companion object {
        const val FREQUENCY_FOR_PICKED = 3
        const val FREQUENCY_FOR_TYPED = 1
        const val FREQUENCY_FOR_AUTO_ADD = 250
        private const val VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED
        private const val PROMOTION_THRESHOLD = 4 * FREQUENCY_FOR_PICKED

        private const val DATABASE_NAME = "auto_dict.db"
        private const val DATABASE_VERSION = 1

        private const val COLUMN_ID = BaseColumns._ID
        private const val COLUMN_WORD = "word"
        private const val COLUMN_FREQUENCY = "freq"
        private const val COLUMN_LOCALE = "locale"

        const val DEFAULT_SORT_ORDER = "$COLUMN_FREQUENCY DESC"
        private const val AUTODICT_TABLE_NAME = "words"

        private val sDictProjectionMap = HashMap<String, String>().apply {
            put(COLUMN_ID, COLUMN_ID)
            put(COLUMN_WORD, COLUMN_WORD)
            put(COLUMN_FREQUENCY, COLUMN_FREQUENCY)
            put(COLUMN_LOCALE, COLUMN_LOCALE)
        }

        private var sOpenHelper: DatabaseHelper? = null
    }
}
