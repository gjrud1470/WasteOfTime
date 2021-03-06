package com.example.wasteoftime

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Process.myUid
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.startForegroundService
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList


class AppOptHolder {
    private var blocked_apps: ArrayList<String> = ArrayList()
    private var cooltime_bool: Boolean = true // default cooltime ON
    private var cooltime: Long = 0.toLong()   // default 30 min
    private var alarmtime: Long = 0.toLong()    // default 30 min
    private var monitoring_flag: Boolean = true //default 모니터링 ON, 쿨타임 및 알람 적용
    private var wakeup_option: Int = 1 // default 연장 가능, 0: 바로 종료, 2: 알림만 띄우기
    //shared preference 이용해서 setting 저장했다 불러오기

    fun get_blocked_apps(): ArrayList<String>? {
        return blocked_apps
    }

    fun set_blocked_apps(list: ArrayList<String>) {
        blocked_apps = list
    }

    fun get_cooltime_bool(): Boolean {
        return cooltime_bool
    }

    fun set_cooltime_bool(option: Boolean) {
        cooltime_bool = option
    }

    fun get_cooltime(): Long {
        return cooltime
    }

    fun set_cooltime(time: Long) {
        cooltime = time
    }

    fun get_alarmtime(): Long {
        return alarmtime
    }

    fun set_alarmtime(time: Long) {
        alarmtime = time
    }

    fun get_monitoring_flag(): Boolean {
        return monitoring_flag
    }

    fun set_monitoring_flag(isChecked: Boolean) {
        monitoring_flag = isChecked
    }

    fun printList() {
        blocked_apps?.forEach {
            Log.wtf("blocked", it)
        }
    }

    fun get_wakeup_opt(): Int {
        return wakeup_option
    }

    fun set_wakeup_opt(opt: Int) {
        wakeup_option = opt
    }
}

val appOptHolder = AppOptHolder()

class MainActivity : AppCompatActivity() {

    private val TAG = "WORK"
    private var appUsageList = ArrayList<AppUsageItem>()

    private var mServiceIntent : Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setup_default_values()

        mServiceIntent = Intent(this, GetForegroundService::class.java)

        if (!checkForPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))    // permission settings
        } else {
            Log.wtf(TAG, "permission granted")
            mServiceIntent.also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!isMyServiceRunning(GetForegroundService::class.java)) {
                        startForegroundService(intent)
                    }
                }
            }
            setAppUsageList(getAppUsageStats())
        }
        setting.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }
    }

    override fun onResume() {
        setAppUsageList(getAppUsageStats())
        super.onResume()
    }

    /*
    override fun onDestroy() {
        stopService(mServiceIntent)
        super.onDestroy()
    } */


    private fun setup_default_values() {
        val pref = getSharedPreferences("UserData", Context.MODE_PRIVATE) as SharedPreferences
        appOptHolder.set_monitoring_flag(pref.getBoolean("monitoring_flag", true))
        appOptHolder.set_cooltime_bool(pref.getBoolean("cooltime_bool", true))
        appOptHolder.set_alarmtime(
            pref.getInt(
                "alarmtime",
                resources.getInteger(R.integer.default_alarm_time)
                        * resources.getInteger(R.integer.min_unit)
            ).toLong()
        )    // default 30 min
        appOptHolder.set_cooltime(
            pref.getInt(
                "cooltime",
                resources.getInteger(R.integer.default_alarm_time)
                        * resources.getInteger(R.integer.min_unit)
            ).toLong()
        )    // default 30 min
        appOptHolder.set_wakeup_opt(pref.getInt("wakeup_opt", 1))

        val blocked_apps = getStringArrayPref("blocked_apps")
        if (blocked_apps != null) {
            appOptHolder.set_blocked_apps(blocked_apps)
        }
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager =
            getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.i("Service status", "Running")
                return true
            }
        }
        Log.i("Service status", "Not running")
        return false
    }

    private fun getStringArrayPref(
        key: String
    ): ArrayList<String>? {
        val pref = getSharedPreferences("UserData", Context.MODE_PRIVATE) as SharedPreferences
        val json = pref.getString(key, null)
        val urls = ArrayList<String>()
        if (json != null) {
            try {
                val a = JSONArray(json)
                for (i in 0 until a.length()) {
                    val url = a.optString(i)
                    urls.add(url)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return urls
    }

    private fun setRecyclerView() {
        val rcView = this.mRecyclerView
        val adapter = AppUsageAdapter(this, appUsageList)
        rcView.adapter = adapter
        findViewById<ConstraintLayout>(R.id.activity_main).invalidate()
    }

    private fun checkForPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(OPSTR_GET_USAGE_STATS, myUid(), packageName)
        return mode == MODE_ALLOWED
    }

    private fun getAppUsageStats(): MutableList<UsageStats> {
        val beginCal = Calendar.getInstance()
        beginCal.add(Calendar.DATE, -1)    // 1
        val endCal = Calendar.getInstance()

        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager // 2
        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, beginCal.timeInMillis, endCal.timeInMillis // 3
        )
        return queryUsageStats
    }

    private fun setAppUsageList(usageStats: MutableList<UsageStats>) {
        appOptHolder.printList()

        if (usageStats.size == 0) {
            Log.wtf(TAG, "empty!")
        }

        appUsageList = ArrayList<AppUsageItem>()
        usageStats.forEach { it ->
            if (appOptHolder.get_blocked_apps()?.contains(it.packageName)!!) { //used more than a second
                Log.wtf("REACH", it.packageName.toString())
                val name = it.packageName
                val idx = appListIdx(name)
                if (idx == -1) {
                    val item = AppUsageItem()
                    val pm = packageManager
                    try {
                        item.setIcon(pm.getApplicationIcon(it.packageName))
                        item.setName(name)

                        item.setUsageTime(it.totalTimeInForeground)
                        appUsageList.add(item) // if the app is already in the list?
                    } catch (e: Exception) {
                        Log.wtf(TAG, "something's gone wrong")
                        e.printStackTrace()
                    }
                    //Log.wtf(TAG, "appName: ${it.packageName.substring(it.packageName.lastIndexOf('.') + 1)}, totalTimeInForeground: ${it.totalTimeInForeground}")
                } else {
                    //Log.d(TAG, "already in list") -> apps with same name?
                    appUsageList.get(idx).setUsageTime(it.totalTimeInForeground)
                }
            }
        }
        appUsageList.sortWith(Comparator { left, right ->
            compareValues(right.getUsageTime(), left.getUsageTime())
        })
        logAppList()
        setRecyclerView()
    }

    private fun appListIdx(name: String): Int {
        if (!appUsageList.isEmpty()) {
            for (i in appUsageList.indices) {
                if (appUsageList.get(i).getName().equals(name)) {
                    //Log.d("Index Found", i.toString())
                    return i
                }
            }
        }
        return -1
    }

    private fun logAppList() {
        Log.wtf("PRINT", "LIST")
        appUsageList.forEach { item ->
            Log.d(TAG, item.getName())
            Log.d(TAG, item.getUsageTime().toString())
        }
    }
}