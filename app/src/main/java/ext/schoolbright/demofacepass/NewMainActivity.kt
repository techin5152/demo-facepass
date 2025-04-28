package ext.schoolbright.demofacepass

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.view.*
import android.content.*
import android.content.res.AssetManager
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.text.SpannableString
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.widget.*
import mcv.facepass.FacePassException
import mcv.facepass.FacePassHandler
import mcv.facepass.types.*
import ext.schoolbright.demofacepass.adapter.FaceTokenAdapter
import ext.schoolbright.demofacepass.adapter.GroupNameAdapter
import ext.schoolbright.demofacepass.camera.CameraManager
import ext.schoolbright.demofacepass.camera.CameraPreview
import ext.schoolbright.demofacepass.camera.CameraPreviewData
import ext.schoolbright.demofacepass.utils.FileUtil
import java.io.File
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class NewMainActivity : Activity(), CameraManager.CameraListener, View.OnClickListener {
    private enum class FacePassSDKMode {
        MODE_ONLINE, MODE_OFFLINE
    }

    /* SDK 实例对象 */
    var mFacePassHandler: FacePassHandler? = null

    /* 相机实例 */
    private var manager: CameraManager? = null

    /* 显示人脸位置角度信息 */
    private val faceBeginTextView: TextView? = null

    /* 显示faceId */
    private var faceEndTextView: TextView? = null

    /* 相机预览界面 */
    private var cameraView: CameraPreview? = null
    private var isLocalGroupExist = false

    /* 在预览界面圈出人脸 */
    private var faceView: FaceView? = null
    private var scrollView: ScrollView? = null

    /* 相机图片旋转角度，请根据实际情况来设置
     * 对于标准设备，可以如下计算旋转角度rotation
     * int windowRotation = ((WindowManager)(getApplicationContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getRotation() * 90;
     * Camera.CameraInfo info = new Camera.CameraInfo();
     * Camera.getCameraInfo(cameraFacingFront ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK, info);
     * int cameraOrientation = info.orientation;
     * int rotation;
     * if (cameraFacingFront) {
     *     rotation = (720 - cameraOrientation - windowRotation) % 360;
     * } else {
     *     rotation = (windowRotation - cameraOrientation + 360) % 360;
     * }
     */
    private var cameraRotation = 0
    private var mSecretNumber = 0
    private var mLastClickTime: Long = 0
    private var heightPixels = 0
    private var widthPixels = 0
    var screenState = 0 // 0 横 1 竖
    var visible: Button? = null
    var ll: LinearLayout? = null
    var frameLayout: FrameLayout? = null
    private var buttonFlag = 0
    private var settingButton: Button? = null
    private var ageGenderEnabledGlobal = false

    /*Toast*/
    private var mRecoToast: Toast? = null

    /*DetectResult queue*/
    var mDetectResultQueue: ArrayBlockingQueue<ByteArray>? = null
    var mFeedFrameQueue: ArrayBlockingQueue<CameraPreviewData>? = null

    /*recognize thread*/
    var mRecognizeThread: RecognizeThread? = null
    var mFeedFrameThread: FeedFrameThread? = null

    /*底库同步*/
    private var mSyncGroupBtn: ImageView? = null
    private var mSyncGroupDialog: AlertDialog? = null
    private var mFaceOperationBtn: ImageView? = null
    private var mAndroidHandler: Handler? = null
    private val mCurrentImage: CameraPreviewData? = null
    private var mSDKModeBtn: Button? = null
    var mId = 0
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDetectResultQueue = ArrayBlockingQueue(5)
        mFeedFrameQueue = ArrayBlockingQueue<CameraPreviewData>(1)
        initAndroidHandler()

        /* 初始化界面 */initView()
        initFacePassSDK()
        initFaceHandler()
        mRecognizeThread = RecognizeThread()
        mRecognizeThread!!.start()
        mFeedFrameThread = FeedFrameThread()
        mFeedFrameThread!!.start()
    }

    private fun initAndroidHandler() {
        mAndroidHandler = Handler()
    }

    private fun initFacePassSDK() {
        //FacePassHandler.getAuth(authIP, apiKey, apiSecret); //McvSafe软授权不需要调用此接口
        FacePassHandler.initSDK(getApplicationContext())
        Log.d("FacePassDemo", FacePassHandler.getVersion())
    }

    private fun initFaceHandler() {
        object : Thread() {
            override fun run() {
                while (true && !isFinishing()) {
                    while (FacePassHandler.isAvailable()) {
                        Log.d(DEBUG_TAG, "start to build FacePassHandler")
                        val config: FacePassConfig
                        try {
                            /* 填入所需要的模型配置 */
                            config = FacePassConfig()
                            config.poseBlurModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "attr.pose_blur.arm.190630.bin"
                            )
                            config.livenessModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "liveness.CPU.rgb.G.bin"
                            )
                            config.searchModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "feat2.arm.K.v1.0_1core.bin"
                            )
                            config.detectModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "detector.arm.G.bin"
                            )
                            config.detectRectModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "detector_rect.arm.G.bin"
                            )
                            config.landmarkModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "pf.lmk.arm.E.bin"
                            )
                            config.rcAttributeModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "attr.RC.arm.F.bin"
                            )
                            config.occlusionFilterModel = FacePassModel.initModel(
                                getApplicationContext().getAssets(),
                                "attr.occlusion.arm.20201209.bin"
                            )
                            //config.smileModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.RC.arm.200815.bin");
                            //config.ageGenderModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.age_gender.arm.190630.bin");

                            /* 送识别阈值参数 */config.rcAttributeAndOcclusionMode = 1
                            config.searchThreshold = 65f
                            config.livenessThreshold = 55f
                            config.livenessEnabled = true
                            config.rgbIrLivenessEnabled = false
                            ageGenderEnabledGlobal = config.ageGenderModel != null
                            config.poseThreshold = FacePassPose(35f, 35f, 35f)
                            config.blurThreshold = 0.8f
                            config.lowBrightnessThreshold = 30f
                            config.highBrightnessThreshold = 210f
                            config.brightnessSTDThreshold = 80f
                            config.faceMinThreshold = 100
                            config.retryCount = 10
                            config.smileEnabled = false
                            config.maxFaceEnabled = true
                            config.fileRootPath =
                                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath

                            /* 创建SDK实例 */mFacePassHandler = FacePassHandler(config)

                            /* 入库阈值参数 */
                            val addFaceConfig: FacePassConfig = mFacePassHandler!!.addFaceConfig
                            addFaceConfig.poseThreshold.pitch = 35f
                            addFaceConfig.poseThreshold.roll = 35f
                            addFaceConfig.poseThreshold.yaw = 35f
                            addFaceConfig.blurThreshold = 0.7f
                            addFaceConfig.lowBrightnessThreshold = 70f
                            addFaceConfig.highBrightnessThreshold = 220f
                            addFaceConfig.brightnessSTDThreshold = 60f
                            addFaceConfig.faceMinThreshold = 100
                            addFaceConfig.rcAttributeAndOcclusionMode = 2
                            mFacePassHandler!!.addFaceConfig = addFaceConfig
                            checkGroup()
                        } catch (e: FacePassException) {
                            e.printStackTrace()
                            Log.d(DEBUG_TAG, "FacePassHandler is null")
                            return
                        }
                        return
                    }
                    try {
                        /* 如果SDK初始化未完成则需等待 */
                        sleep(500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    protected override fun onResume() {
        checkGroup()
        initToast()
        /* 打开相机 */manager!!.open(getWindowManager(), false, cameraWidth, cameraHeight)
        adaptFrameLayout()
        super.onResume()
    }

    private fun checkGroup() {
        if (mFacePassHandler == null) {
            return
        }
        try {
            val localGroups: Array<String> = mFacePassHandler!!.getLocalGroups()
            isLocalGroupExist = false
            if (localGroups == null || localGroups.size == 0) {
                faceView!!.post { toast("请创建" + group_name + "底库") }
                return
            }
            for (group in localGroups) {
                if (group_name == group) {
                    isLocalGroupExist = true
                }
            }
            if (!isLocalGroupExist) {
                faceView!!.post { toast("请创建" + group_name + "底库") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /* 相机回调函数 */
    override fun onPictureTaken(cameraPreviewData: CameraPreviewData) {
        mFeedFrameQueue!!.offer(cameraPreviewData)
        Log.i(DEBUG_TAG, "feedframe")
    }

    inner class FeedFrameThread : Thread() {
        var isInterrupt = false
        override fun run() {
            while (!isInterrupt) {
                val cameraPreviewData = try {
                    mFeedFrameQueue!!.take()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    continue
                }
                if (mFacePassHandler == null) {
                    continue
                }
                /* 将相机预览帧转成SDK算法所需帧的格式 FacePassImage */
                val startTime = System.currentTimeMillis() //起始时间
                val image: FacePassImage = try {
                    FacePassImage(
                        cameraPreviewData.nv21Data,
                        cameraPreviewData.width,
                        cameraPreviewData.height,
                        cameraRotation,
                        FacePassImageType.NV21
                    )
                } catch (e: FacePassException) {
                    e.printStackTrace()
                    continue
                }

                /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
                var detectionResult: FacePassDetectionResult? = null
                try {
                    detectionResult = mFacePassHandler?.feedFrame(image)
                } catch (e: FacePassException) {
                    e.printStackTrace()
                }
                if (!(detectionResult != null && detectionResult.faceList.isNotEmpty())) {
                    /* 当前帧没有检出人脸 */
                    runOnUiThread(Runnable {
                        faceView!!.clear()
                        faceView!!.invalidate()
                    })
                } else {
                    /* 将识别到的人脸在预览界面中圈出，并在上方显示人脸位置及角度信息 */
                    val bufferFaceList: Array<FacePassFace> = detectionResult.faceList
                    runOnUiThread(Runnable { showFacePassFace(bufferFaceList) })
                }
                if (SDK_MODE == FacePassSDKMode.MODE_OFFLINE) {
                    /*离线模式，将识别到人脸的，message不为空的result添加到处理队列中*/
                    if (detectionResult != null && detectionResult.message.size !== 0) {
                        Log.d(DEBUG_TAG, "mDetectResultQueue.offer")
                        mDetectResultQueue!!.offer(detectionResult.message)
                    }
                }
                val endTime = System.currentTimeMillis() //结束时间
                val runTime = endTime - startTime
                Log.i("]time", String.format("feedfream %d ms", runTime))
            }
        }

        override fun interrupt() {
            isInterrupt = true
            super.interrupt()
        }
    }

    fun findidx(results: Array<FacePassAgeGenderResult>?, trackId: Long): Int {
        val result = -1
        if (results == null) {
            return result
        }
        for (i in results.indices) {
            if (results[i].trackId === trackId) {
                return i
            }
        }
        return result
    }

    inner class RecognizeThread : Thread() {
        var isInterrupt = false
        override fun run() {
            while (!isInterrupt) {
                try {
                    val detectionResult = mDetectResultQueue!!.take()
                    val ageGenderResult: Array<FacePassAgeGenderResult>? = null
                    //if (ageGenderEnabledGlobal) {
                    //    ageGenderResult = mFacePassHandler!!.getAgeGender(detectionResult);
                    //    for (FacePassAgeGenderResult t : ageGenderResult) {
                    //        Log.e("FacePassAgeGenderResult", "id " + t.trackId + " age " + t.age + " gender " + t.gender);
                    //    }
                    //}
                    Log.d(DEBUG_TAG, "mDetectResultQueue.isLocalGroupExist")
                    if (isLocalGroupExist) {
                        Log.d(DEBUG_TAG, "mDetectResultQueue.recognize")
                        val recognizeResult: Array<FacePassRecognitionResult> =
                            mFacePassHandler!!.recognize(group_name, detectionResult)
                        if (recognizeResult != null && recognizeResult.isNotEmpty()) {
                            for (result in recognizeResult) {
                                val faceToken: String = String(result.faceToken)
                                if (FacePassRecognitionState.RECOGNITION_PASS === result.recognitionState) {
                                    getFaceImageByFaceToken(result.trackId, faceToken)
                                }
                                val idx = findidx(ageGenderResult, result.trackId)
                                if (idx == -1) {
                                    showRecognizeResult(
                                        result.trackId,
                                        result.detail.searchScore,
                                        result.detail.livenessScore,
                                        !TextUtils.isEmpty(faceToken)
                                    )
                                } else {
                                    showRecognizeResult(
                                        result.trackId,
                                        result.detail.searchScore,
                                        result.detail.livenessScore,
                                        !TextUtils.isEmpty(faceToken),
                                        ageGenderResult!![idx].age,
                                        ageGenderResult[idx].gender
                                    )
                                }
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: FacePassException) {
                    e.printStackTrace()
                }
            }
        }

        override fun interrupt() {
            isInterrupt = true
            super.interrupt()
        }
    }

    private fun showRecognizeResult(
        trackId: Long,
        searchScore: Float,
        livenessScore: Float,
        isRecognizeOK: Boolean
    ) {
        mAndroidHandler!!.post {
            faceEndTextView?.append(
                """
            ID = $trackId${if (isRecognizeOK) "识别成功" else "识别失败"}
            
            """.trimIndent()
            )
            faceEndTextView?.append("识别分 = $searchScore\n")
            faceEndTextView?.append("活体分 = $livenessScore\n")
            scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun showRecognizeResult(
        trackId: Long,
        searchScore: Float,
        livenessScore: Float,
        isRecognizeOK: Boolean,
        age: Float,
        gender: Int
    ) {
        mAndroidHandler!!.post {
            faceEndTextView?.append(
                """
    ID = $trackId${if (isRecognizeOK) "识别成功" else "识别失败"}
    
    """.trimIndent()
            )
            faceEndTextView?.append("识别分 = $searchScore\n")
            faceEndTextView?.append("活体分 = $livenessScore\n")
            faceEndTextView?.append("年龄 = $age\n")
            if (gender == 0) {
                faceEndTextView?.append(
                    """
    性别 = 男
    
    """.trimIndent()
                )
            } else if (gender == 1) {
                faceEndTextView?.append(
                    """
    性别 = 女
    
    """.trimIndent()
                )
            } else {
                faceEndTextView?.append(
                    """
    性别 = unknown
    
    """.trimIndent()
                )
            }
            scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun adaptFrameLayout() {
        SettingVar.isButtonInvisible = false
        SettingVar.iscameraNeedConfig = false
    }

    private fun initToast() {
        SettingVar.isButtonInvisible = false
    }

    private fun initView() {
        val windowRotation: Int =
            (getApplicationContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager).getDefaultDisplay()
                .getRotation() * 90
        cameraRotation = if (windowRotation == 0) {
            FacePassImageRotation.DEG90
        } else if (windowRotation == 90) {
            FacePassImageRotation.DEG0
        } else if (windowRotation == 270) {
            FacePassImageRotation.DEG180
        } else {
            FacePassImageRotation.DEG270
        }
        Log.i(DEBUG_TAG, "Rotation: cameraRation: $cameraRotation")
        cameraFacingFront = true
        val preferences: SharedPreferences =
            getSharedPreferences(SettingVar.SharedPrefrence, Context.MODE_PRIVATE)
        SettingVar.isSettingAvailable =
            preferences.getBoolean("isSettingAvailable", SettingVar.isSettingAvailable)
        SettingVar.isCross = preferences.getBoolean("isCross", SettingVar.isCross)
        SettingVar.faceRotation = preferences.getInt("faceRotation", SettingVar.faceRotation)
        SettingVar.cameraPreviewRotation =
            preferences.getInt("cameraPreviewRotation", SettingVar.cameraPreviewRotation)
        SettingVar.cameraFacingFront =
            preferences.getBoolean("cameraFacingFront", SettingVar.cameraFacingFront)
        if (SettingVar.isSettingAvailable) {
            cameraRotation = SettingVar.faceRotation
            cameraFacingFront = SettingVar.cameraFacingFront
        }
        Log.i(DEBUG_TAG, "Rotation: screenRotation: $windowRotation")
        Log.i(DEBUG_TAG, "Rotation: faceRotation: " + SettingVar.faceRotation)
        Log.i(DEBUG_TAG, "Rotation: new cameraRation: $cameraRotation")
        val mCurrentOrientation: Int = getResources().getConfiguration().orientation
        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            screenState = 1
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenState = 0
        }
        setContentView(R.layout.activity_main)
        mSyncGroupBtn = findViewById<View>(R.id.btn_group_name) as ImageView?
        mSyncGroupBtn!!.setOnClickListener(this)
        mFaceOperationBtn = findViewById<View>(R.id.btn_face_operation) as ImageView?
        mFaceOperationBtn!!.setOnClickListener(this)
        val displayMetrics = DisplayMetrics()
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics)
        heightPixels = displayMetrics.heightPixels
        widthPixels = displayMetrics.widthPixels
        SettingVar.mHeight = heightPixels
        SettingVar.mWidth = widthPixels
        scrollView = findViewById<View>(R.id.scrollView) as ScrollView?
        val mgr: AssetManager = getAssets()
        val tf: Typeface = Typeface.createFromAsset(mgr, "fonts/Univers LT 57 Condensed.ttf")
        /* 初始化界面 */faceEndTextView = this.findViewById<View>(R.id.tv_meg2) as TextView?
        faceEndTextView?.setTypeface(tf)
        faceView = this.findViewById<View>(R.id.fcview) as FaceView?
        settingButton = this.findViewById<View>(R.id.settingid) as Button?
        settingButton!!.setOnClickListener {
            val curTime = System.currentTimeMillis()
            val durTime = curTime - mLastClickTime
            mLastClickTime = curTime
            if (durTime < CLICK_INTERVAL) {
                ++mSecretNumber
                if (mSecretNumber == 5) {
                    val intent = Intent(this@NewMainActivity, SettingActivity::class.java)
                    startActivity(intent)
                    this@NewMainActivity.finish()
                }
            } else {
                mSecretNumber = 0
            }
        }
        SettingVar.cameraSettingOk = false
        ll = this.findViewById<View>(R.id.ll) as LinearLayout?
        ll?.background?.alpha = 100
        visible = this.findViewById<View>(R.id.visible) as Button?
        visible!!.setBackgroundResource(R.drawable.debug)
        visible!!.setOnClickListener {
            if (buttonFlag == 0) {
                ll?.visibility = View.VISIBLE
                if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    visible!!.setBackgroundResource(R.drawable.down)
                } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    visible!!.setBackgroundResource(R.drawable.right)
                }
                buttonFlag = 1
            } else if (buttonFlag == 1) {
                buttonFlag = 0
                if (SettingVar.isButtonInvisible) ll?.visibility =
                    View.INVISIBLE else ll?.visibility =
                    View.GONE
                visible!!.setBackgroundResource(R.drawable.debug)
            }
        }
        manager = CameraManager()
        cameraView = findViewById<View>(R.id.preview) as CameraPreview?
        manager!!.setPreviewDisplay(cameraView)
        frameLayout = findViewById<View>(R.id.frame) as FrameLayout?
        /* 注册相机回调函数 */manager!!.setListener(this)
        mSDKModeBtn = findViewById<View>(R.id.btn_mode_switch) as Button?
        mSDKModeBtn!!.text = SDK_MODE.toString()
        mSDKModeBtn!!.setOnClickListener { }
    }

    protected override fun onStop() {
        SettingVar.isButtonInvisible = false
        mDetectResultQueue!!.clear()
        if (manager != null) {
            manager!!.release()
        }
        super.onStop()
    }

    protected override fun onRestart() {
        faceView!!.clear()
        faceView!!.invalidate()
        super.onRestart()
    }

    protected override fun onDestroy() {
        mRecognizeThread!!.isInterrupt = true
        mFeedFrameThread!!.isInterrupt = true
        mRecognizeThread!!.interrupt()
        mFeedFrameThread!!.interrupt()
        if (manager != null) {
            manager!!.release()
        }
        if (mAndroidHandler != null) {
            mAndroidHandler!!.removeCallbacksAndMessages(null)
        }
        mFacePassHandler?.release()
        super.onDestroy()
    }

    private fun showFacePassFace(detectResult: Array<FacePassFace>) {
        faceView!!.clear()
        for (face in detectResult) {
            Log.d(
                "facefacelist",
                "width " + (face.rect.right - face.rect.left).toString() + " height " + (face.rect.bottom - face.rect.top)
            )
            Log.d("facefacelist", "smile " + face.smile)
            val mirror = cameraFacingFront /* 前摄像头时mirror为true */
            val faceIdString = StringBuilder()
            faceIdString.append("ID = ").append(face.trackId)
            val faceViewString = SpannableString(faceIdString)
            faceViewString.setSpan(
                TypefaceSpan("fonts/kai"),
                0,
                faceViewString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val faceRollString = StringBuilder()
            faceRollString.append("旋转: ").append(face.pose.roll.toInt()).append("°")
            val facePitchString = StringBuilder()
            facePitchString.append("上下: ").append(face.pose.pitch.toInt()).append("°")
            val faceYawString = StringBuilder()
            faceYawString.append("左右: ").append(face.pose.yaw.toInt()).append("°")
            val faceBlurString = StringBuilder()
            faceBlurString.append("模糊: ").append(face.blur)
            val smileString = StringBuilder()
            smileString.append("微笑: ").append(java.lang.String.format("%.6f", face.smile))
            val mat = Matrix()
            val w: Int = cameraView!!.measuredWidth
            val h: Int = cameraView!!.measuredHeight
            val cameraHeight = manager!!.cameraheight
            val cameraWidth = manager!!.cameraWidth
            var left = 0f
            var top = 0f
            var right = 0f
            var bottom = 0f
            when (cameraRotation) {
                0 -> {
                    left = face.rect.left.toFloat()
                    top = face.rect.top.toFloat()
                    right = face.rect.right.toFloat()
                    bottom = face.rect.bottom.toFloat()
                    val scale = if (mirror) -1f else 1f
                    val translate = if (mirror) cameraHeight.toFloat() else 0f
                    mat.setScale(scale, 1f)
                    mat.postTranslate(translate, 0f)
                    mat.postScale(
                        w.toFloat() / cameraWidth.toFloat(),
                        h.toFloat() / cameraHeight.toFloat()
                    )
                }
                90 -> {
                    val scale = if (mirror) -1f else 1f
                    val translate = if (mirror) cameraHeight.toFloat() else 0f
                    mat.setScale(scale, 1f)
                    mat.postTranslate(translate, 0f)
                    mat.postScale(
                        w.toFloat() / cameraHeight.toFloat(),
                        h.toFloat() / cameraWidth.toFloat()
                    )
                    left = face.rect.top.toFloat()
                    top = (cameraWidth - face.rect.right).toFloat()
                    right = face.rect.bottom.toFloat()
                    bottom = (cameraWidth - face.rect.left).toFloat()
                }
                180 -> {
                    val scale = if (mirror) -1f else 1f
                    val translate = if (mirror) cameraHeight.toFloat() else 0f
                    mat.setScale(scale, 1f)
                    mat.postTranslate(0f, translate)
                    mat.postScale(
                        w.toFloat() / cameraWidth.toFloat(),
                        h.toFloat() / cameraHeight.toFloat()
                    )
                    left = face.rect.right.toFloat()
                    top = face.rect.bottom.toFloat()
                    right = face.rect.left.toFloat()
                    bottom = face.rect.top.toFloat()
                }
                270 -> {
                    val scale = if (mirror) -1f else 1f
                    val translate = if (mirror) cameraHeight.toFloat() else 0f
                    mat.setScale(scale, 1f)
                    mat.postTranslate(translate, 0f)
                    mat.postScale(
                        w.toFloat() / cameraHeight.toFloat(),
                        h.toFloat() / cameraWidth.toFloat()
                    )
                    left = cameraHeight.toFloat() - face.rect.bottom.toFloat()
                    top = face.rect.left.toFloat()
                    right = cameraHeight.toFloat() - face.rect.top.toFloat()
                    bottom = face.rect.right.toFloat()
                }
            }
            val drect = RectF()
            val srect = RectF(left, top, right, bottom)
            mat.mapRect(drect, srect)
            faceView!!.addRect(drect)
            faceView!!.addId(faceIdString.toString())
            faceView!!.addRoll(faceRollString.toString())
            faceView!!.addPitch(facePitchString.toString())
            faceView!!.addYaw(faceYawString.toString())
            faceView!!.addBlur(faceBlurString.toString())
            faceView!!.addSmile(smileString.toString())
        }
        faceView!!.invalidate()
    }

    fun showToast(text: CharSequence?, duration: Int, isSuccess: Boolean, bitmap: Bitmap?) {
        val inflater: LayoutInflater = getLayoutInflater()
        val toastView: View = inflater.inflate(R.layout.toast, null)
        val toastLLayout: LinearLayout = toastView.findViewById<View>(R.id.toastll) as LinearLayout
            ?: return
        toastLLayout.getBackground().setAlpha(100)
        val imageView = toastView.findViewById<View>(R.id.toastImageView) as ImageView
        val idTextView: TextView = toastView.findViewById<View>(R.id.toastTextView) as TextView
        val stateView: TextView = toastView.findViewById<View>(R.id.toastState) as TextView
        val s: SpannableString
        if (isSuccess) {
            s = SpannableString("验证成功")
            imageView.setImageResource(R.drawable.success)
        } else {
            s = SpannableString("验证失败")
            imageView.setImageResource(R.drawable.success)
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        }
        stateView.setText(s)
        idTextView.setText(text)
        if (mRecoToast == null) {
            mRecoToast = Toast(getApplicationContext())
            mRecoToast!!.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
        }
        mRecoToast!!.setDuration(duration)
        mRecoToast!!.setView(toastView)
        mRecoToast!!.show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_group_name -> showSyncGroupDialog()
            R.id.btn_face_operation -> showAddFaceDialog()
        }
    }

    @SuppressLint("Range")
    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_CHOOSE_PICK -> if (resultCode == RESULT_OK) {
                var path: String? = ""
                val uri = data?.data
                val pojo = arrayOf(MediaStore.Images.Media.DATA)
                val cursorLoader = CursorLoader(this, uri, pojo, null, null, null)
                val cursor = cursorLoader.loadInBackground()
                if (cursor != null) {
                    cursor.moveToFirst()
                    path = cursor.getString(cursor.getColumnIndex(pojo[0]))
                }
                if (uri != null) {
                    if (!TextUtils.isEmpty(path) && "file".equals(uri.scheme, ignoreCase = true)) {
                        path = uri.path.toString()
                    }
                }
                if (TextUtils.isEmpty(path)) {
                    try {
                        path = FileUtil.getPath(applicationContext, uri)
                    } catch (e: Exception) {
                        print("$e")
                    }
                }
                if (TextUtils.isEmpty(path)) {
                    toast("图片选取失败！")
                    return
                }
                if (!TextUtils.isEmpty(path) && mFaceOperationDialog != null && mFaceOperationDialog!!.isShowing()) {
                    val imagePathEdt: EditText =
                        mFaceOperationDialog!!.findViewById<View>(R.id.et_face_image_path) as EditText
                    imagePathEdt.setText(path)
                }
            }
        }
    }

    private fun getFaceImageByFaceToken(trackId: Long, faceToken: String) {
        if (TextUtils.isEmpty(faceToken)) {
            return
        }
        try {
            val bitmap: Bitmap = mFacePassHandler!!.getFaceImage(faceToken.toByteArray())
            mAndroidHandler!!.post {
                Log.i(DEBUG_TAG, "getFaceImageByFaceToken cache is null")
                showToast("ID = $trackId", Toast.LENGTH_SHORT, true, bitmap)
            }
            if (bitmap != null) {
                return
            }
        } catch (e: FacePassException) {
            e.printStackTrace()
        }
    }

    /*同步底库操作*/
    private fun showSyncGroupDialog() {
        if (mSyncGroupDialog != null && mSyncGroupDialog!!.isShowing()) {
            mSyncGroupDialog!!.hide()
        }
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val view: View = LayoutInflater.from(this).inflate(R.layout.layout_dialog_sync_groups, null)
        val groupNameEt: EditText = view.findViewById<View>(R.id.et_group_name) as EditText
        val syncDataTv: TextView = view.findViewById<View>(R.id.tv_show_sync_data) as TextView
        val obtainGroupsBtn = view.findViewById<View>(R.id.btn_obtain_groups) as Button
        val createGroupBtn = view.findViewById<View>(R.id.btn_submit) as Button
        val closeWindowIv = view.findViewById<View>(R.id.iv_close) as ImageView
        val handleSyncDataBtn = view.findViewById<View>(R.id.btn_handle_sync_data) as Button
        val groupNameLv = view.findViewById<View>(R.id.lv_group_name) as ListView
        val syncScrollView: ScrollView =
            view.findViewById<View>(R.id.sv_handle_sync_data) as ScrollView
        val groupNameAdapter = GroupNameAdapter()
        builder.setView(view)
        closeWindowIv.setOnClickListener { mSyncGroupDialog!!.dismiss() }
        obtainGroupsBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            try {
                val groups: Array<String> = mFacePassHandler!!.getLocalGroups()
                if (groups != null && groups.size > 0) {
                    val data = Arrays.asList(*groups)
                    syncScrollView.setVisibility(View.GONE)
                    groupNameLv.visibility = View.VISIBLE
                    groupNameAdapter.setData(data)
                    groupNameLv.adapter = groupNameAdapter
                } else {
                    toast("groups is null !")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
        createGroupBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            val groupName: String = groupNameEt.getText().toString()
            if (TextUtils.isEmpty(groupName)) {
                toast("please input group name ！")
                return@OnClickListener
            }
            var isSuccess = false
            try {
                isSuccess = mFacePassHandler!!.createLocalGroup(groupName)
            } catch (e: FacePassException) {
                e.printStackTrace()
            }
            toast("create group $isSuccess")
            if (isSuccess && group_name == groupName) {
                isLocalGroupExist = true
            }
        })
        groupNameAdapter.setOnItemDeleteButtonClickListener(object :
            GroupNameAdapter.ItemDeleteButtonClickListener {
            override fun OnItemDeleteButtonClickListener(position: Int) {
                val groupNames: List<String> = groupNameAdapter.getData() ?: return
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ")
                    return
                }
                val groupName = groupNames[position]
                var isSuccess = false
                try {
                    isSuccess = mFacePassHandler!!.deleteLocalGroup(groupName)
                    if (isSuccess) {
                        val groups: Array<String> = mFacePassHandler!!.getLocalGroups()
                        if (group_name == groupName) {
                            isLocalGroupExist = false
                        }
                        if (groups != null) {
                            groupNameAdapter.setData(Arrays.asList(*groups))
                            groupNameAdapter.notifyDataSetChanged()
                        }
                        toast("删除成功!")
                    } else {
                        toast("删除失败!")
                    }
                } catch (e: FacePassException) {
                    e.printStackTrace()
                }
            }
        })
        mSyncGroupDialog = builder.create()
        val m: WindowManager = getWindowManager()
        val d: Display = m.getDefaultDisplay() //为获取屏幕宽、高
        val attributes = mSyncGroupDialog?.window!!.attributes
        attributes.height = d.getHeight()
        attributes.width = d.getWidth()
        mSyncGroupDialog?.getWindow()!!.setAttributes(attributes)
        mSyncGroupDialog?.show()
    }

    private var mFaceOperationDialog: AlertDialog? = null
    private fun showAddFaceDialog() {
        if (mFaceOperationDialog != null && !mFaceOperationDialog!!.isShowing) {
            mFaceOperationDialog!!.show()
            return
        }
        if (mFaceOperationDialog != null && mFaceOperationDialog!!.isShowing) {
            return
        }
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val view: View =
            LayoutInflater.from(this).inflate(R.layout.layout_dialog_face_operation, null)
        builder.setView(view)
        val faceImagePathEt: EditText = view.findViewById<View>(R.id.et_face_image_path) as EditText
        val faceTokenEt: EditText = view.findViewById<View>(R.id.et_face_token) as EditText
        val groupNameEt: EditText = view.findViewById<View>(R.id.et_group_name) as EditText
        val choosePictureBtn = view.findViewById<View>(R.id.btn_choose_picture) as Button
        val addFaceBtn = view.findViewById<View>(R.id.btn_add_face) as Button
        val getFaceImageBtn = view.findViewById<View>(R.id.btn_get_face_image) as Button
        val deleteFaceBtn = view.findViewById<View>(R.id.btn_delete_face) as Button
        val bindGroupFaceTokenBtn = view.findViewById<View>(R.id.btn_bind_group) as Button
        val getGroupInfoBtn = view.findViewById<View>(R.id.btn_get_group_info) as Button
        val closeIv = view.findViewById<View>(R.id.iv_close) as ImageView
        val groupInfoLv = view.findViewById<View>(R.id.lv_group_info) as ListView
        val faceTokenAdapter = FaceTokenAdapter()
        groupNameEt.setText(group_name)
        closeIv.setOnClickListener { mFaceOperationDialog!!.dismiss() }
        choosePictureBtn.setOnClickListener {
            val intentFromGallery = Intent(Intent.ACTION_GET_CONTENT)
            intentFromGallery.setType("image/*") // 设置文件类型
            intentFromGallery.addCategory(Intent.CATEGORY_OPENABLE)
            try {
                startActivityForResult(intentFromGallery, REQUEST_CODE_CHOOSE_PICK)
            } catch (e: ActivityNotFoundException) {
                toast("请安装相册或者文件管理器")
            }
        }
        addFaceBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            val imagePath: String = faceImagePathEt.getText().toString()
            if (TextUtils.isEmpty(imagePath)) {
                toast("请输入正确的图片路径！")
                return@OnClickListener
            }
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                toast("图片不存在 ！")
                return@OnClickListener
            }
            val bitmap: Bitmap = BitmapFactory.decodeFile(imagePath)
            try {
                val result: FacePassAddFaceResult = mFacePassHandler!!.addFace(bitmap)
                if (result != null) {
                    if (result.result === 0) {
                        toast("add face successfully！")
                        faceTokenEt.setText(String(result.faceToken))
                    } else if (result.result === 1) {
                        toast("no face ！")
                    } else {
                        toast("quality problem！")
                    }
                }
            } catch (e: FacePassException) {
                e.printStackTrace()
                toast(e.message)
            }
        })
        getFaceImageBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            try {
                val faceToken: ByteArray = faceTokenEt.getText().toString().toByteArray()
                val bmp: Bitmap = mFacePassHandler!!.getFaceImage(faceToken)
                val iv = findViewById<View>(R.id.imview) as ImageView
                iv.setImageBitmap(bmp)
                iv.visibility = View.VISIBLE
                iv.postDelayed({
                    iv.visibility = View.GONE
                    iv.setImageBitmap(null)
                }, 2000)
                mFaceOperationDialog!!.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        })
        deleteFaceBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            var b = false
            try {
                val faceToken: ByteArray = faceTokenEt.getText().toString().toByteArray()
                b = mFacePassHandler!!.deleteFace(faceToken)
                if (b) {
                    val groupName: String = groupNameEt.getText().toString()
                    if (TextUtils.isEmpty(groupName)) {
                        toast("group name  is null ！")
                        return@OnClickListener
                    }
                    val faceTokens: Array<ByteArray> =
                        mFacePassHandler!!.getLocalGroupInfo(groupName)
                    val faceTokenList: MutableList<String> = ArrayList()
                    if (faceTokens != null && faceTokens.size > 0) {
                        for (j in faceTokens.indices) {
                            if (faceTokens[j].size > 0) {
                                faceTokenList.add(String(faceTokens[j]))
                            }
                        }
                    }
                    faceTokenAdapter.setData(faceTokenList)
                    groupInfoLv.adapter = faceTokenAdapter
                }
            } catch (e: FacePassException) {
                e.printStackTrace()
                toast(e.message)
            }
            val result = if (b) "success " else "failed"
            toast("delete face $result")
            Log.d(DEBUG_TAG, "delete face  $result")
        })
        bindGroupFaceTokenBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            val faceToken: ByteArray = faceTokenEt.getText().toString().toByteArray()
            val groupName: String = groupNameEt.getText().toString()
            if (faceToken == null || faceToken.size == 0 || TextUtils.isEmpty(groupName)) {
                toast("params error！")
                return@OnClickListener
            }
            try {
                val b: Boolean = mFacePassHandler!!.bindGroup(groupName, faceToken)
                val result = if (b) "success " else "failed"
                toast("bind  $result")
            } catch (e: Exception) {
                e.printStackTrace()
                toast(e.message)
            }
        })
        getGroupInfoBtn.setOnClickListener(View.OnClickListener {
            if (mFacePassHandler == null) {
                toast("FacePassHandle is null ! ")
                return@OnClickListener
            }
            val groupName: String = groupNameEt.getText().toString()
            if (TextUtils.isEmpty(groupName)) {
                toast("group name  is null ！")
                return@OnClickListener
            }
            try {
                val faceTokens: Array<ByteArray> = mFacePassHandler!!.getLocalGroupInfo(groupName)
                val faceTokenList: MutableList<String> = ArrayList()
                if (faceTokens != null && faceTokens.size > 0) {
                    for (j in faceTokens.indices) {
                        if (faceTokens[j].size > 0) {
                            faceTokenList.add(String(faceTokens[j]))
                        }
                    }
                }
                faceTokenAdapter.setData(faceTokenList)
                groupInfoLv.adapter = faceTokenAdapter
            } catch (e: Exception) {
                e.printStackTrace()
                toast("get local group info error!")
            }
        })
        faceTokenAdapter.setOnItemButtonClickListener(object :
            FaceTokenAdapter.ItemButtonClickListener {
            override fun onItemDeleteButtonClickListener(position: Int) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ")
                    return
                }
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ")
                    return
                }
                val groupName: String = groupNameEt.getText().toString()
                if (TextUtils.isEmpty(groupName)) {
                    toast("group name  is null ！")
                    return
                }
                try {
                    val faceToken: ByteArray =
                        faceTokenAdapter.getData().get(position).toByteArray()
                    val b: Boolean = mFacePassHandler!!.deleteFace(faceToken)
                    val result = if (b) "success " else "failed"
                    toast("delete face $result")
                    if (b) {
                        val faceTokens: Array<ByteArray> =
                            mFacePassHandler!!.getLocalGroupInfo(groupName)
                        val faceTokenList: MutableList<String> = ArrayList()
                        if (faceTokens != null && faceTokens.size > 0) {
                            for (j in faceTokens.indices) {
                                if (faceTokens[j].size > 0) {
                                    faceTokenList.add(String(faceTokens[j]))
                                }
                            }
                        }
                        faceTokenAdapter.setData(faceTokenList)
                        groupInfoLv.adapter = faceTokenAdapter
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message)
                }
            }

            override fun onItemUnbindButtonClickListener(position: Int) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ")
                    return
                }
                val groupName: String = groupNameEt.getText().toString()
                if (TextUtils.isEmpty(groupName)) {
                    toast("group name  is null ！")
                    return
                }
                try {
                    val faceToken: ByteArray =
                        faceTokenAdapter.getData().get(position).toByteArray()
                    val b: Boolean = mFacePassHandler!!.unBindGroup(groupName, faceToken)
                    val result = if (b) "success " else "failed"
                    toast("unbind $result")
                    if (b) {
                        val faceTokens: Array<ByteArray> =
                            mFacePassHandler!!.getLocalGroupInfo(groupName)
                        val faceTokenList: MutableList<String> = ArrayList()
                        if (faceTokens != null && faceTokens.size > 0) {
                            for (j in faceTokens.indices) {
                                if (faceTokens[j].size > 0) {
                                    faceTokenList.add(String(faceTokens[j]))
                                }
                            }
                        }
                        faceTokenAdapter.setData(faceTokenList)
                        groupInfoLv.adapter = faceTokenAdapter
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast("unbind error!")
                }
            }
        })
        val m: WindowManager = getWindowManager()
        val d: Display = m.getDefaultDisplay() //为获取屏幕宽、高
        mFaceOperationDialog = builder.create()
        val attributes: WindowManager.LayoutParams =
            mFaceOperationDialog!!.getWindow()!!.getAttributes()
        attributes.height = d.getHeight()
        attributes.width = d.getWidth()
        mFaceOperationDialog!!.getWindow()!!.setAttributes(attributes)
        mFaceOperationDialog!!.show()
    }

    private fun toast(msg: String?) {
        Toast.makeText(this@NewMainActivity, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val SDK_MODE = FacePassSDKMode.MODE_OFFLINE
        private const val DEBUG_TAG = "FacePassDemo"
        private const val MSG_SHOW_TOAST = 1
        private const val DELAY_MILLION_SHOW_TOAST = 2000
        private val recognize_url: String? = null

        /* 人脸识别Group */
        private const val group_name = "facepass"

        /* 相机是否使用前置摄像头 */
        private var cameraFacingFront = true
        private const val cameraWidth = 1280
        private const val cameraHeight = 720
        private const val CLICK_INTERVAL: Long = 600
        private const val REQUEST_CODE_CHOOSE_PICK = 1
    }
}