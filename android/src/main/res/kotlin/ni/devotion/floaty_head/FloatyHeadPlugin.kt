package ni.devotion.floaty_head

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build

import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import io.flutter.view.FlutterCallbackInformation

import io.flutter.view.FlutterNativeView
import io.flutter.view.FlutterRunArguments
import ni.devotion.floaty_head.services.FloatyContentJobService.Companion.INTENT_EXTRA_IS_UPDATE_WINDOW
import ni.devotion.floaty_head.services.FloatyContentJobService
import ni.devotion.floaty_head.utils.Commons.getMapFromObject
import ni.devotion.floaty_head.utils.Constants.SHARED_PREF_FLOATY_HEAD
import ni.devotion.floaty_head.utils.Constants.CALLBACK_HANDLE_KEY
import ni.devotion.floaty_head.utils.Constants.CODE_CALLBACK_HANDLE_KEY
import ni.devotion.floaty_head.utils.Constants.BACKGROUND_CHANNEL
import ni.devotion.floaty_head.utils.Constants.METHOD_CHANNEL
import ni.devotion.floaty_head.utils.Constants.KEY_BODY
import ni.devotion.floaty_head.utils.Constants.KEY_FOOTER
import ni.devotion.floaty_head.utils.Constants.KEY_HEADER
import ni.devotion.floaty_head.utils.Managment
import ni.devotion.floaty_head.utils.Managment.bodyMap
import ni.devotion.floaty_head.utils.Managment.bodyView
import ni.devotion.floaty_head.utils.Managment.footerMap
import ni.devotion.floaty_head.utils.Managment.footerView
import ni.devotion.floaty_head.utils.Managment.headerView
import ni.devotion.floaty_head.utils.Managment.headersMap
import ni.devotion.floaty_head.utils.Managment.layoutParams
import ni.devotion.floaty_head.views.BodyView
import ni.devotion.floaty_head.views.FooterView
import ni.devotion.floaty_head.views.HeaderView
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import kotlin.collections.List


class FloatyHeadPlugin :FlutterActivity(), MethodCallHandler, ActivityAware {
    companion object {
        var mBound: Boolean = false
        lateinit var instance: FloatyHeadPlugin

        var context:Context?=null
        private var channel: MethodChannel? = null
        private var backgroundChannel: MethodChannel? = null
    }

    private val CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084
    private var sBackgroundFlutterView: FlutterNativeView? = null


    fun startCallBackHandler(context: Context) {
        val preferences = context.getSharedPreferences(SHARED_PREF_FLOATY_HEAD, 0)
        val callBackHandle: Long = preferences.getLong(CALLBACK_HANDLE_KEY, -1)
        if (callBackHandle != -1L) {
            FlutterLoader().ensureInitializationComplete(context, null)
            val mAppBundlePath: String =   FlutterLoader().findAppBundlePath()
            val flutterCallback: FlutterCallbackInformation = FlutterCallbackInformation.lookupCallbackInformation(callBackHandle)
            sBackgroundFlutterView?.let { sbfv ->
                backgroundChannel ?: run {
                    backgroundChannel = MethodChannel(sbfv, BACKGROUND_CHANNEL)
                }
                Managment.sIsIsolateRunning.set(true)
            } ?: run {
                sBackgroundFlutterView = FlutterNativeView(context, true)
                if(!Managment.sIsIsolateRunning.get()) {
                    Managment.pluginRegistrantC ?: run {
                        Log.i("TAG", "Unable to start callBackHandle... as plugin is not registered")
                        return
                    }
                    val args = FlutterRunArguments()
                    args.bundlePath = mAppBundlePath
                    args.entrypoint = flutterCallback.callbackName
                    args.libraryPath = flutterCallback.callbackLibraryPath
                    sBackgroundFlutterView!!.runFromBundle(args)
                    Managment.pluginRegistrantC?.registerWith(sBackgroundFlutterView!!.pluginRegistry)
                    backgroundChannel = MethodChannel(sBackgroundFlutterView!!, BACKGROUND_CHANNEL)
                    Managment.sIsIsolateRunning.set(true)
                }
                Managment.sIsIsolateRunning.set(true)
            }
        }
    }

    fun invokeCallBack( type: String, params: Any) {
        val argumentsList: MutableList<Any> = ArrayList()
        val preferences = activity.applicationContext.getSharedPreferences(SHARED_PREF_FLOATY_HEAD, 0)
        val codeCallBackHandle = preferences.getLong(CODE_CALLBACK_HANDLE_KEY, -1)
        if (codeCallBackHandle == -1L) {
            Log.e("TAG", "Back failed, as codeCallBackHandle is null")
        } else {
            argumentsList.clear()
            argumentsList.add(codeCallBackHandle)
            argumentsList.add(type)
            argumentsList.add(params)
            if(Managment.sIsIsolateRunning.get()) {
                backgroundChannel ?: run{
                    backgroundChannel = MethodChannel(sBackgroundFlutterView!!.dartExecutor.binaryMessenger, BACKGROUND_CHANNEL)
                }
                try {
                    val retries = intArrayOf(2)
                    invokeCallBackToFlutter(backgroundChannel!!, "callBack", argumentsList, retries)
                    //channel!!.invokeMethod("callBack", argumentsList);
                }catch (ex: Exception) {
                    Log.e("TAG", "Exception in invoking callback $ex")
                }
            } else {
                Log.e("TAG", "invokeCallBack failed, as isolate is not running")
            }
        }
    }

    private fun invokeCallBackToFlutter(channel: MethodChannel, method: String, arguments: List<Any>, retries: IntArray) {
        channel.invokeMethod(method, arguments, object : Result {
            override fun success(o: Any?) {
                Log.i("TAG", "Invoke call back success")
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                Log.e("TAG", "Error $errorCode$errorMessage")
            }


            override fun notImplemented() {
                if (retries[0] > 0) {
                    Log.d("TAG", "Not Implemented method $method. Trying again to check if it works")
                    invokeCallBackToFlutter(channel, method, arguments, retries)
                } else {
                    Log.e("TAG", "Not Implemented method $method")
                }
                retries[0]--
            }
        })
    }

    override fun onMethodCall(call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "start" -> {
                Managment.globalContext = activity.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
                    val packageName = activity.packageName
                    activity.startActivityForResult(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                            CODE_DRAW_OVER_OTHER_APP_PERMISSION)
                } else {
                    mBound = if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                        val subIntent = Intent(activity.applicationContext, FloatyContentJobService::class.java)
                        subIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        subIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        subIntent.putExtra(INTENT_EXTRA_IS_UPDATE_WINDOW, true)
                        activity.startService(subIntent)
                        true
                    } else {
                        val subIntent = Intent(activity.applicationContext, FloatyContentJobService::class.java)
                        activity.startForegroundService(subIntent)
                        true
                    }
                }
            }
            "isOpen" -> result.success(mBound)
            "close" -> {
                if(mBound){
                    FloatyContentJobService.instance!!.closeWindow(true)
                    if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q){
                        activity.stopService(Intent(activity.applicationContext, FloatyContentJobService::class.java))
                    }else{
                        activity.startForegroundService(Intent(activity.applicationContext, FloatyContentJobService::class.java))
                    }
                    mBound = false
                }
            }
            "setIcon" -> result.success(setIconFromAsset(call.arguments as String))
            "setBackgroundCloseIcon" -> result.success(setBackgroundCloseIconFromAsset(call.arguments as String))
            "setCloseIcon" -> result.success(setCloseIconFromAsset(call.arguments as String))
            "setNotificationTitle" -> result.success(setNotificationTitle(call.arguments as String))
            "setNotificationIcon" -> result.success(setNotificationIcon(call.arguments as String))
            "setFloatyHeadContent" -> {
                assert((call.arguments != null))
                val updateParams = call.arguments as HashMap<String, Any>
                headersMap = getMapFromObject(updateParams, KEY_HEADER)
                bodyMap = getMapFromObject(updateParams, KEY_BODY)
                footerMap = getMapFromObject(updateParams, KEY_FOOTER)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                try {
                    headersMap?.let {
                        headerView = HeaderView(activity.applicationContext, it).view
                    }
                    bodyMap?.let {
                        bodyView = BodyView(activity.applicationContext, it).view
                    }
                    footerMap?.let {
                        footerView = FooterView(activity.applicationContext, it).view
                    }
                } catch (except: Exception) {
                    except.printStackTrace()
                }
                result.success(true)
            }
            "registerCallBackHandler" -> {
                try {
                    val arguments = call.arguments as List<*>
                    arguments ?: result.success(false)
                    arguments.let {
                        val callBackHandle = (it[0]).toString().toLong()
                        val onClickHandle = (it[1]).toString().toLong()
                        val preferences = activity.applicationContext!!.getSharedPreferences(SHARED_PREF_FLOATY_HEAD, 0)
                        preferences?.edit()?.putLong(CALLBACK_HANDLE_KEY, callBackHandle)!!.apply()
                        preferences.edit()?.putLong(CODE_CALLBACK_HANDLE_KEY, onClickHandle)!!.apply()
                        startCallBackHandler(activity.applicationContext)
                        result.success(true)
                    }
                } catch (ex: Exception) {
                    Log.e("TAG", "Exception in registerOnClickHandler $ex")
                    result.success(false)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun setNotificationTitle(title: String):Int {
        var result = -1
        try {
            Managment.notificationTitle = title
            result = 1
        }catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun setNotificationIcon(assetPath: String):Int {
        var result = -1
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val inputStream = activity.applicationContext.assets.open("flutter_assets/$assetPath")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                Managment.notificationIcon = bitmap
                result = 1
            } else {
                val assetLookupKey = FlutterLoader().getLookupKeyForAsset(assetPath)
                val assetManager = activity.applicationContext.assets
                val assetFileDescriptor = assetManager.openFd(assetLookupKey)
                val inputStream = assetFileDescriptor.createInputStream()
                Managment.notificationIcon = BitmapFactory.decodeStream(inputStream)
                result = 1
            }
        }catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun setBackgroundCloseIconFromAsset(assetPath: String):Int {
        var result = -1
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val inputStream = activity.applicationContext.assets.open("flutter_assets/$assetPath")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                Managment.backgroundCloseIcon = bitmap
                result = 1
            }
            else {
                val assetLookupKey = FlutterLoader().getLookupKeyForAsset(assetPath)
                val assetManager = activity.applicationContext.assets
                val assetFileDescriptor = assetManager.openFd(assetLookupKey)
                val inputStream = assetFileDescriptor.createInputStream()
                Managment.backgroundCloseIcon = BitmapFactory.decodeStream(inputStream)
                result = 1
            }
        }catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun setCloseIconFromAsset(assetPath: String):Int {
        var result = -1
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                val inputStream = activity.applicationContext.assets.open("flutter_assets/$assetPath")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                Managment.closeIcon = bitmap
                result = 1
            }
            else {
                val assetLookupKey = FlutterLoader().getLookupKeyForAsset(assetPath)
                val assetManager = activity.applicationContext.assets
                val assetFileDescriptor = assetManager.openFd(assetLookupKey)
                val inputStream = assetFileDescriptor.createInputStream()
                Managment.closeIcon = BitmapFactory.decodeStream(inputStream)
                result = 1
            }
        }catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun setIconFromAsset(assetPath: String):Int {
        var result = -1
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val inputStream = activity.applicationContext.assets.open("flutter_assets/$assetPath")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                Managment.floatingIcon = bitmap
                result = 1
            }
            else {
                val assetLookupKey = FlutterLoader().getLookupKeyForAsset(assetPath)
                val assetManager = activity.applicationContext.assets
                val assetFileDescriptor = assetManager.openFd(assetLookupKey)
                val inputStream = assetFileDescriptor.createInputStream()
                Managment.floatingIcon = BitmapFactory.decodeStream(inputStream)
                result = 1
            }
        }catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }



  override fun onAttachedToActivity(binding: ActivityPluginBinding) {

      Managment.activity = binding.activity
      instance = this@FloatyHeadPlugin
  }

  override fun onDetachedFromActivity() {
      //release()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {

      Managment.activity = binding.activity
      instance = this@FloatyHeadPlugin
  }

  override fun onDetachedFromActivityForConfigChanges() {
      //release()
  }
}
