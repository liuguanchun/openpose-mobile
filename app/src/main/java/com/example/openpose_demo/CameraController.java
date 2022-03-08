package com.example.openpose_demo;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;


import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
public class CameraController {
    private static final String TAG = "CameraController";
    private static Activity mActivity;
    private String mFolderPath; //保存视频,图片的文件夹路径
    private ImageReader mImageReader;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private AutoFitTextureView mTextureView;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);//一个信号量以防止应用程序在关闭相机之前退出。
    private String mCameraId;//当前相机的ID。
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private Integer mSensorOrientation;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mPreviewBuilder;

    private static final int MAX_PREVIEW_WIDTH = 1920;//Camera2 API保证的最大预览宽度

    private static final int MAX_PREVIEW_HEIGHT = 1080;//Camera2 API保证的最大预览高度

    private boolean mFlashSupported;
    private int mState = STATE_PREVIEW;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final int STATE_PREVIEW = 0;//相机状态：显示相机预览。
    private static final int STATE_WAITING_LOCK = 1;//相机状态：等待焦点被锁定。
    private static final int STATE_WAITING_PRECAPTURE = 2;//等待曝光被Precapture状态。
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;//相机状态：等待曝光的状态是不是Precapture。
    private static final int STATE_PICTURE_TAKEN = 4;//相机状态：拍照。
    private MediaRecorder mMediaRecorder;
    private String mNextVideoAbsolutePath;
    private  String videopath;


    private CameraController() {

    }

    private static class ClassHolder {

        static CameraController mInstance = new CameraController();

    }

    public static CameraController getmInstance(Activity activity) {
        mActivity = activity;
        return ClassHolder.mInstance;
    }

    public void InitCamera(AutoFitTextureView textureView) {
        this.mTextureView = textureView;
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * 设置需要保存文件的文件夹路径
     * @param path
     */
    public void setFolderPath(String path) {

        this.mFolderPath = path;
        File mFolder = new File(path);
        if (!mFolder.exists()) {
            mFolder.mkdirs();
            System.out.println(path);
            if (!mFolder.exists()){
                Log.d(TAG, "文件夹创建失败" );
            }
            Log.d(TAG, "文件夹不存在去创建");
        } else {
            Log.d(TAG, "文件夹已创建");
        }
    }

    public String getFolderPath() {
        return mFolderPath;
    }

    /**
     * 拍照
     */
//    public void takePicture() {
//        lockFocus();
//    }

    /**
     * 开始录像
     */
    public void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            //为相机预览设置曲面
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            //设置MediaRecorder的表面
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // 启动捕获会话
            // 一旦会话开始，我们就可以更新UI并开始录制
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            //开启录像
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {


                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 停止录像
     */
    public void stopRecordingVideo() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
            } catch (IllegalStateException e) {
                // TODO 如果当前java状态和jni里面的状态不一致，
                //e.printStackTrace();
                mMediaRecorder = null;
                mMediaRecorder = new MediaRecorder();
            }
            mMediaRecorder.reset();
            mNextVideoAbsolutePath = null;
            startPreview();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); //设置用于录制的音源
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//开始捕捉和编码数据到setOutputFile（指定的文件）
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); //设置在录制过程中产生的输出文件的格式
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath();
            videopath=mNextVideoAbsolutePath;
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);//设置输出文件的路径
        mMediaRecorder.setVideoEncodingBitRate(10000000);//设置录制的视频编码比特率
        mMediaRecorder.setVideoFrameRate(25);//设置要捕获的视频帧速率
        mMediaRecorder.setVideoSize(640, 480);//设置要捕获的视频的宽度和高度
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//设置视频编码器，用于录制
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置audio的编码格式
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "setUpMediaRecorder: "+rotation);


        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }
/*
*
* 返回录像视频地址
*
* */
    public String getVideoPath(){
        return videopath;
    }


    private String getVideoFilePath() {

        return getFolderPath() + "/" + System.currentTimeMillis() + ".mp4";
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

    }

    /**
     * 开启相机
     */
    private void openCamera(int width, int height) {
        //设置相机输出
        setUpCameraOutputs(width, height);
        //配置变换
        configureTransform(width, height);
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                return;
            }
            //打开相机预览
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * CameraDevice状态更改时被调用。
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // 打开相机时调用此方法。 在这里开始相机预览。
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            //创建CameraPreviewSession
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mActivity.finish();
        }

    };

    /**
     * 为相机预览创建新的CameraCaptureSession
     */
    private void startPreview() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;


            // 将默认缓冲区的大小配置为我们想要的相机预览的大小。 设置分辨率
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // 预览的输出Surface。
            Surface surface = new Surface(texture);

            //设置了一个具有输出Surface的CaptureRequest.Builder。
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            //创建一个CameraCaptureSession来进行相机预览。
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // 相机已经关闭
                            if (null == mCameraDevice) {
                                return;
                            }

                            // 会话准备好后，我们开始显示预览
                            mCaptureSession = cameraCaptureSession;
                            // 自动对焦应
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                            // 最终开启相机预览并添加事件
                            mPreviewRequest = mPreviewRequestBuilder.build();
                            try {
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {

                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理与JPEG捕获有关的事件
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

        }

    };



    private ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

        }
    };

    /**
     * 设置与相机相关的成员变量。
     *
     * @param width  相机预览的可用尺寸宽度
     * @param height 相机预览的可用尺寸的高度
     */
    private void setUpCameraOutputs(int width, int height) {

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        //获取摄像头列表
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                //不使用前置摄像
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;//不结束循环,只跳出本次循环
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                //对于静态图像拍照, 使用最大的可用尺寸
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea()
                );

                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);

                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                //获取手机旋转的角度以调整图片的方向

                int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;

                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        //横屏
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        //竖屏
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                DisplayMetrics displayMetrics = new DisplayMetrics();
                mActivity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int widthPixels = displayMetrics.widthPixels;
                int heightPixels = displayMetrics.heightPixels;
                Log.d(TAG, "widthPixels: " + widthPixels + "____heightPixels:" + heightPixels);

                //尝试使用太大的预览大小可能会超出摄像头的带宽限制
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewWidth, maxPreviewWidth,
                        maxPreviewHeight, largest);

                //我们将TextureView的宽高比与我们选择的预览大小相匹配。这样设置不会拉伸,但是不能全屏展示
//                int orientation = mActivity.getResources().getConfiguration().orientation;
//                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    //横屏
////                    mTextureView.setAspectRatio(
////                            mPreviewSize.getWidth(),mPreviewSize.getHeight());
//                    Log.d(TAG, "横屏: "+"width:"+mPreviewSize.getWidth()+"____height:"+mPreviewSize.getHeight());
//
//                } else {
//                    // 竖屏
////                    mTextureView.setAspectRatio(
////                            widthPixels, heightPixels);
//
//                    Log.d(TAG, "竖屏: "+"____height:"+mPreviewSize.getHeight()+"width:"+mPreviewSize.getWidth());
//                }

                mMediaRecorder = new MediaRecorder();

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {


        }

    }

    /**
     * 根据他们的区域比较两个Size
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // 我们在这里投放，以确保乘法不会溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // 收集支持的分辨率，这些分辨率至少与预览图面一样大
        List<Size> bigEnough = new ArrayList<>();
        // 收集小于预览表面的支持分辨率
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        //挑一个足够大的最小的。如果没有一个足够大的，就挑一个不够大的。
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.d(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }



}
