package org.pocketworkstation.pckeyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class PluginManager(private val mIME: LatinIME) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Package information changed, updating dictionaries.")
        getPluginDictionaries(context)
        Log.i(TAG, "Finished updating dictionaries.")
        mIME.toggleLanguage(true, true)
    }

    interface DictPluginSpec {
        fun getDict(context: Context): BinaryDictionary?
    }

    private abstract class DictPluginSpecBase : DictPluginSpec {
        var mPackageName: String? = null

        fun getResources(context: Context): Resources? {
            val packageManager = context.packageManager
            return try {
                val appInfo = packageManager.getApplicationInfo(mPackageName!!, 0)
                packageManager.getResourcesForApplication(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(TAG, "couldn't get resources")
                null
            }
        }

        abstract fun getStreams(res: Resources): Array<InputStream>?

        override fun getDict(context: Context): BinaryDictionary? {
            val res = getResources(context) ?: return null
            val dicts = getStreams(res) ?: return null
            val dict = BinaryDictionary(context, dicts, Suggest.DIC_MAIN)
            if (dict.getSize() == 0) return null
            return dict
        }
    }

    private class DictPluginSpecHK(pkg: String, private val mRawIds: IntArray?) : DictPluginSpecBase() {
        init {
            mPackageName = pkg
        }

        override fun getStreams(res: Resources): Array<InputStream>? {
            if (mRawIds == null || mRawIds.isEmpty()) return null
            return Array(mRawIds.size) { i ->
                res.openRawResource(mRawIds[i])
            }
        }
    }

    private class DictPluginSpecSoftKeyboard(
        pkg: String,
        private val mAssetName: String?,
        private val mResId: Int
    ) : DictPluginSpecBase() {
        init {
            mPackageName = pkg
        }

        override fun getStreams(res: Resources): Array<InputStream>? {
            if (mAssetName == null) {
                if (mResId == 0) return null
                val a = res.obtainTypedArray(mResId)
                val resIds: IntArray
                try {
                    resIds = IntArray(a.length()) { i -> a.getResourceId(i, 0) }
                } finally {
                    a.recycle()
                }
                return Array(resIds.size) { i ->
                    res.openRawResource(resIds[i])
                }
            } else {
                return try {
                    val inputStream = res.assets.open(mAssetName)
                    arrayOf(inputStream)
                } catch (e: IOException) {
                    Log.e(TAG, "Dictionary asset loading failure")
                    null
                }
            }
        }
    }

    companion object {
        private const val TAG = "PCKeyboard"
        private const val HK_INTENT_DICT = "org.pocketworkstation.DICT"
        private const val SOFTKEYBOARD_INTENT_DICT = "com.menny.android.anysoftkeyboard.DICTIONARY"
        private const val SOFTKEYBOARD_DICT_RESOURCE_METADATA_NAME =
            "com.menny.android.anysoftkeyboard.dictionaries"

        private val SOFTKEYBOARD_LANG_MAP = hashMapOf(
            "dk" to "da"
        )

        private val mPluginDicts = HashMap<String, DictPluginSpec>()

        @JvmStatic
        fun getSoftKeyboardDictionaries(packageManager: PackageManager) {
            val dictIntent = Intent(SOFTKEYBOARD_INTENT_DICT)
            val dictPacks = packageManager.queryBroadcastReceivers(
                dictIntent, PackageManager.GET_META_DATA
            )
            for (ri in dictPacks) {
                val appInfo = ri.activityInfo.applicationInfo
                val pkgName = appInfo.packageName
                var success = false
                try {
                    val res = packageManager.getResourcesForApplication(appInfo)
                    var dictId = res.getIdentifier("dictionaries", "xml", pkgName)
                    if (dictId == 0) {
                        try {
                            dictId = ri.activityInfo.metaData.getInt(SOFTKEYBOARD_DICT_RESOURCE_METADATA_NAME)
                        } catch (e: Exception) {
                        }
                    }
                    if (dictId == 0) continue

                    val xrp = res.getXml(dictId)
                    var assetName: String? = null
                    var resId = 0
                    var lang: String? = null
                    try {
                        var current = xrp.eventType
                        while (current != org.xmlpull.v1.XmlResourceParser.END_DOCUMENT) {
                            if (current == org.xmlpull.v1.XmlResourceParser.START_TAG) {
                                val tag = xrp.name
                                if (tag == "Dictionary") {
                                    lang = xrp.getAttributeValue(null, "locale")
                                    val convLang = SOFTKEYBOARD_LANG_MAP[lang]
                                    if (convLang != null) lang = convLang
                                    val type = xrp.getAttributeValue(null, "type")
                                    if (type == null || type == "raw" || type == "binary" || type == "binary_resource") {
                                        assetName = xrp.getAttributeValue(null, "dictionaryAssertName")
                                        resId = xrp.getAttributeResourceValue(null, "dictionaryResourceId", 0)
                                    } else {
                                        Log.w(TAG, "Unsupported AnySoftKeyboard dict type $type")
                                    }
                                }
                            }
                            xrp.next()
                            current = xrp.eventType
                        }
                    } catch (e: XmlPullParserException) {
                        Log.e(TAG, "Dictionary XML parsing failure")
                    } catch (e: IOException) {
                        Log.e(TAG, "Dictionary XML IOException")
                    }

                    if ((assetName == null && resId == 0) || lang == null) continue
                    val spec: DictPluginSpec = DictPluginSpecSoftKeyboard(pkgName, assetName, resId)
                    mPluginDicts[lang] = spec
                    Log.i(TAG, "Found plugin dictionary: lang=$lang, pkg=$pkgName")
                    success = true
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.i(TAG, "bad")
                } finally {
                    if (!success) {
                        Log.i(TAG, "failed to load plugin dictionary spec from $pkgName")
                    }
                }
            }
        }

        @JvmStatic
        fun getHKDictionaries(packageManager: PackageManager) {
            val dictIntent = Intent(HK_INTENT_DICT)
            val dictPacks = packageManager.queryIntentActivities(dictIntent, 0)
            for (ri in dictPacks) {
                val appInfo = ri.activityInfo.applicationInfo
                val pkgName = appInfo.packageName
                var success = false
                try {
                    val res = packageManager.getResourcesForApplication(appInfo)
                    val langId = res.getIdentifier("dict_language", "string", pkgName)
                    if (langId == 0) continue
                    val lang = res.getString(langId)
                    var rawIds: IntArray?

                    val rawId = res.getIdentifier("main", "raw", pkgName)
                    if (rawId != 0) {
                        rawIds = intArrayOf(rawId)
                    } else {
                        var parts = 0
                        val ids = ArrayList<Int>()
                        while (true) {
                            val id = res.getIdentifier("main$parts", "raw", pkgName)
                            if (id == 0) break
                            ids.add(id)
                            ++parts
                        }
                        if (parts == 0) continue
                        rawIds = IntArray(parts) { i -> ids[i] }
                    }
                    val spec: DictPluginSpec = DictPluginSpecHK(pkgName, rawIds)
                    mPluginDicts[lang] = spec
                    Log.i(TAG, "Found plugin dictionary: lang=$lang, pkg=$pkgName")
                    success = true
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.i(TAG, "bad")
                } finally {
                    if (!success) {
                        Log.i(TAG, "failed to load plugin dictionary spec from $pkgName")
                    }
                }
            }
        }

        @JvmStatic
        fun getPluginDictionaries(context: Context) {
            mPluginDicts.clear()
            val packageManager = context.packageManager
            getSoftKeyboardDictionaries(packageManager)
            getHKDictionaries(packageManager)
        }

        @JvmStatic
        fun getDictionary(context: Context, lang: String): BinaryDictionary? {
            var spec = mPluginDicts[lang]
            if (spec == null) spec = mPluginDicts[lang.substring(0, 2)]
            if (spec == null) {
                return null
            }
            val dict = spec.getDict(context)
            Log.i(TAG, "Found plugin dictionary for $lang${if (dict == null) " is null" else ", size=${dict.getSize()}"}")
            return dict
        }
    }
}
