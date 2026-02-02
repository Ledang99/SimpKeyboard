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
 * Stores all the pairs user types in databases. Prune the database if the size
 * gets too big. Unlike AutoDictionary, it even stores the pairs that are already
 * in the dictionary.
 */
class UserBigramDictionary(
    context: Context,
    private val mIme: LatinIME?,
    private val mLocale: String?,
    dicTypeId: Int
) : ExpandableDictionary(context, dicTypeId) {

    private var mPendingWrites = HashSet<Bigram>()
    private val mPendingWritesLock = Any()

    init {
        if (sOpenHelper == null) {
            sOpenHelper = DatabaseHelper(getContext())
        }
        if (mLocale != null && mLocale.length > 1) {
            loadDictionary()
        }
    }

    private data class Bigram(
        val word1: String,
        val word2: String,
        var frequency: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is Bigram) return false
            return word1 == other.word1 && word2 == other.word2
        }

        override fun hashCode(): Int {
            return "$word1 $word2".hashCode()
        }
    }

    fun setDatabaseMax(maxUserBigram: Int) {
        sMaxUserBigrams = maxUserBigram
    }

    fun setDatabaseDelete(deleteUserBigram: Int) {
        sDeleteUserBigrams = deleteUserBigram
    }

    override fun close() {
        flushPendingWrites()
        super.close()
    }

    /**
     * Pair will be added to the userbigram database.
     */
    fun addBigrams(word1: String, word2: String): Int {
        var processedWord2 = word2
        if (mIme != null && mIme.currentWord.isAutoCapitalized) {
            processedWord2 = Character.toLowerCase(processedWord2[0]) + processedWord2.substring(1)
        }

        var freq = super.addBigram(word1, processedWord2, FREQUENCY_FOR_TYPED)
        if (freq > FREQUENCY_MAX) freq = FREQUENCY_MAX
        synchronized(mPendingWritesLock) {
            if (freq == FREQUENCY_FOR_TYPED || mPendingWrites.isEmpty()) {
                mPendingWrites.add(Bigram(word1, processedWord2, freq))
            } else {
                val bi = Bigram(word1, processedWord2, freq)
                mPendingWrites.remove(bi)
                mPendingWrites.add(bi)
            }
        }

        return freq
    }

    /**
     * Schedules a background thread to write any pending words to the database.
     */
    fun flushPendingWrites() {
        synchronized(mPendingWritesLock) {
            if (mPendingWrites.isEmpty()) return
            UpdateDbTask(getContext(), sOpenHelper!!, mPendingWrites, mLocale ?: "").execute()
            mPendingWrites = HashSet()
        }
    }

    fun waitUntilUpdateDBDone() {
        synchronized(mPendingWritesLock) {
            while (sUpdatingDB) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                }
            }
        }
    }

    override fun loadDictionaryAsync() {
        val cursor = query(MAIN_COLUMN_LOCALE + "=?", arrayOf(mLocale ?: ""))
        try {
            if (cursor.moveToFirst()) {
                val word1Index = cursor.getColumnIndex(MAIN_COLUMN_WORD1)
                val word2Index = cursor.getColumnIndex(MAIN_COLUMN_WORD2)
                val frequencyIndex = cursor.getColumnIndex(FREQ_COLUMN_FREQUENCY)
                while (!cursor.isAfterLast) {
                    val word1 = cursor.getString(word1Index)
                    val word2 = cursor.getString(word2Index)
                    val frequency = cursor.getInt(frequencyIndex)
                    if (word1.length < MAX_WORD_LENGTH && word2.length < MAX_WORD_LENGTH) {
                        super.setBigram(word1, word2, frequency)
                    }
                    cursor.moveToNext()
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun query(selection: String, selectionArgs: Array<String>) = run {
        val qb = SQLiteQueryBuilder()
        qb.tables = "$MAIN_TABLE_NAME INNER JOIN $FREQ_TABLE_NAME ON (" +
                "$MAIN_TABLE_NAME.$MAIN_COLUMN_ID=$FREQ_TABLE_NAME.$FREQ_COLUMN_PAIR_ID)"
        qb.projectionMap = sDictProjectionMap
        val db = sOpenHelper!!.readableDatabase
        qb.query(
            db,
            arrayOf(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD2, FREQ_COLUMN_FREQUENCY),
            selection, selectionArgs, null, null, null
        )
    }

    private class DatabaseHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL(
                "CREATE TABLE $MAIN_TABLE_NAME (" +
                        "$MAIN_COLUMN_ID INTEGER PRIMARY KEY," +
                        "$MAIN_COLUMN_WORD1 TEXT," +
                        "$MAIN_COLUMN_WORD2 TEXT," +
                        "$MAIN_COLUMN_LOCALE TEXT" +
                        ");"
            )
            db.execSQL(
                "CREATE TABLE $FREQ_TABLE_NAME (" +
                        "$FREQ_COLUMN_ID INTEGER PRIMARY KEY," +
                        "$FREQ_COLUMN_PAIR_ID INTEGER," +
                        "$FREQ_COLUMN_FREQUENCY INTEGER," +
                        "FOREIGN KEY($FREQ_COLUMN_PAIR_ID) REFERENCES $MAIN_TABLE_NAME" +
                        "($MAIN_COLUMN_ID) ON DELETE CASCADE" +
                        ");"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w(
                TAG, "Upgrading database from version $oldVersion to " +
                        "$newVersion, which will destroy all old data"
            )
            db.execSQL("DROP TABLE IF EXISTS $MAIN_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $FREQ_TABLE_NAME")
            onCreate(db)
        }
    }

    private class UpdateDbTask(
        context: Context,
        private val mDbHelper: DatabaseHelper,
        private val mMap: HashSet<Bigram>,
        private val mLocale: String
    ) : AsyncTask<Void, Void, Void>() {

        private fun checkPruneData(db: SQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON;")
            val c = db.query(
                FREQ_TABLE_NAME, arrayOf(FREQ_COLUMN_PAIR_ID),
                null, null, null, null, null
            )
            try {
                val totalRowCount = c.count
                if (totalRowCount > sMaxUserBigrams) {
                    val numDeleteRows = (totalRowCount - sMaxUserBigrams) + sDeleteUserBigrams
                    val pairIdColumnId = c.getColumnIndex(FREQ_COLUMN_PAIR_ID)
                    c.moveToFirst()
                    var count = 0
                    while (count < numDeleteRows && !c.isAfterLast) {
                        val pairId = c.getString(pairIdColumnId)
                        db.delete(
                            MAIN_TABLE_NAME, "$MAIN_COLUMN_ID=?",
                            arrayOf(pairId)
                        )
                        c.moveToNext()
                        count++
                    }
                }
            } finally {
                c.close()
            }
        }

        override fun onPreExecute() {
            sUpdatingDB = true
        }

        override fun doInBackground(vararg v: Void): Void? {
            val db = mDbHelper.writableDatabase
            db.execSQL("PRAGMA foreign_keys = ON;")
            for (bi in mMap) {
                val c = db.query(
                    MAIN_TABLE_NAME, arrayOf(MAIN_COLUMN_ID),
                    "$MAIN_COLUMN_WORD1=? AND $MAIN_COLUMN_WORD2=? AND $MAIN_COLUMN_LOCALE=?",
                    arrayOf(bi.word1, bi.word2, mLocale), null, null, null
                )

                val pairId: Int
                if (c.moveToFirst()) {
                    pairId = c.getInt(c.getColumnIndex(MAIN_COLUMN_ID))
                    db.delete(
                        FREQ_TABLE_NAME, "$FREQ_COLUMN_PAIR_ID=?",
                        arrayOf(pairId.toString())
                    )
                } else {
                    val pairIdLong = db.insert(
                        MAIN_TABLE_NAME, null,
                        getContentValues(bi.word1, bi.word2, mLocale)
                    )
                    pairId = pairIdLong.toInt()
                }
                c.close()

                db.insert(FREQ_TABLE_NAME, null, getFrequencyContentValues(pairId, bi.frequency))
            }
            checkPruneData(db)
            sUpdatingDB = false

            return null
        }

        private fun getContentValues(word1: String, word2: String, locale: String): ContentValues {
            val values = ContentValues(3)
            values.put(MAIN_COLUMN_WORD1, word1)
            values.put(MAIN_COLUMN_WORD2, word2)
            values.put(MAIN_COLUMN_LOCALE, locale)
            return values
        }

        private fun getFrequencyContentValues(pairId: Int, frequency: Int): ContentValues {
            val values = ContentValues(2)
            values.put(FREQ_COLUMN_PAIR_ID, pairId)
            values.put(FREQ_COLUMN_FREQUENCY, frequency)
            return values
        }
    }

    companion object {
        private const val TAG = "UserBigramDictionary"

        private const val FREQUENCY_FOR_TYPED = 2
        private const val FREQUENCY_MAX = 127

        const val SUGGEST_THRESHOLD = 6 * FREQUENCY_FOR_TYPED

        private var sMaxUserBigrams = 10000
        private var sDeleteUserBigrams = 1000

        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "userbigram_dict.db"

        private const val MAIN_TABLE_NAME = "main"
        private const val MAIN_COLUMN_ID = BaseColumns._ID
        private const val MAIN_COLUMN_WORD1 = "word1"
        private const val MAIN_COLUMN_WORD2 = "word2"
        private const val MAIN_COLUMN_LOCALE = "locale"

        private const val FREQ_TABLE_NAME = "frequency"
        private const val FREQ_COLUMN_ID = BaseColumns._ID
        private const val FREQ_COLUMN_PAIR_ID = "pair_id"
        private const val FREQ_COLUMN_FREQUENCY = "freq"

        @Volatile
        private var sUpdatingDB = false

        private val sDictProjectionMap = HashMap<String, String>().apply {
            put(MAIN_COLUMN_ID, MAIN_COLUMN_ID)
            put(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD1)
            put(MAIN_COLUMN_WORD2, MAIN_COLUMN_WORD2)
            put(MAIN_COLUMN_LOCALE, MAIN_COLUMN_LOCALE)
            put(FREQ_COLUMN_ID, FREQ_COLUMN_ID)
            put(FREQ_COLUMN_PAIR_ID, FREQ_COLUMN_PAIR_ID)
            put(FREQ_COLUMN_FREQUENCY, FREQ_COLUMN_FREQUENCY)
        }

        private var sOpenHelper: DatabaseHelper? = null
    }
}
