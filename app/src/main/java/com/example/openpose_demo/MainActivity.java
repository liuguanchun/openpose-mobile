//package com.example.openpose_demo;
//
//
//import android.Manifest;
//import android.app.Activity;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.content.res.Configuration;
//import android.graphics.ImageFormat;
//import android.graphics.Matrix;
//import android.graphics.RectF;
//import android.graphics.SurfaceTexture;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraCaptureSession;
//import android.hardware.camera2.CameraCharacteristics;
//import android.hardware.camera2.CameraDevice;
//import android.hardware.camera2.CameraManager;
//import android.hardware.camera2.CameraMetadata;
//import android.hardware.camera2.CaptureRequest;
//import android.hardware.camera2.params.StreamConfigurationMap;
//import android.media.MediaRecorder;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.util.Log;
//import android.util.Size;
//import android.util.SparseIntArray;
//import android.view.Surface;
//import android.view.SurfaceHolder;
//import android.view.TextureView;
//import android.widget.Button;
//import android.widget.FrameLayout;
//import android.widget.ImageButton;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.RequiresApi;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.List;
//
//import static android.content.ContentValues.TAG;
//
//@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//public class MainActivity extends Activity
//{
//    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
//
//    static {
//        ORIENTATIONS.append(Surface.ROTATION_0, 90);
//        ORIENTATIONS.append(Surface.ROTATION_90, 0);
//        ORIENTATIONS.append(Surface.ROTATION_180, 270);
//        ORIENTATIONS.append(Surface.ROTATION_270, 180);
//    }
//    // 定义界面上根布局管理器
//    private FrameLayout rootLayout;
//    // 定义自定义的AutoFitTextureView组件,用于预览摄像头照片
//    private AutoFitTextureView textureView;
//    // 摄像头ID（通常0代表后置摄像头，1代表前置摄像头）
//    private String mCameraId = "0";
//    // 定义代表摄像头的成员变量
//    private CameraDevice cameraDevice;
//    // 预览尺寸
//    private Size previewSize;
//    private CaptureRequest.Builder previewRequestBuilder;
//    // 定义用于预览照片的捕获请求
//    private CaptureRequest previewRequest;
//    // 定义CameraCaptureSession成员变量
//    private CameraCaptureSession captureSession;
//    // 定义MediaRecorder
//    private MediaRecorder mMediaRecorder;
//    private Size mVideoSize;
//    private Size mPreviewSize;
//    //拍摄、停止按钮
//    private Button record;
//    private Button stop;
//
//    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
//            = new TextureView.SurfaceTextureListener()
//    {
//        @RequiresApi(api = Build.VERSION_CODES.M)
//        @Override
//        public void onSurfaceTextureAvailable(SurfaceTexture texture
//                , int width, int height)
//        {
//            // 当TextureView可用时，打开摄像头
//            openCamera(width, height);
//        }
//
//        @Override
//        public void onSurfaceTextureSizeChanged(SurfaceTexture texture
//                , int width, int height)
//        {
//            configureTransform(width, height);
//        }
//
//        @Override
//        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture)
//        {
//            return true;
//        }
//
//        @Override
//        public void onSurfaceTextureUpdated(SurfaceTexture texture)
//        {
//        }
//    };
//    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
//    {
//        //  摄像头被打开时激发该方法
//        @Override
//        public void onOpened(@NonNull CameraDevice cameraDevice)
//        {
//            MainActivity.this.cameraDevice = cameraDevice;
//            // 开始预览
//            createCameraPreviewSession();  // ②
//        }
//
//        // 摄像头断开连接时激发该方法
//        @Override
//        public void onDisconnected(CameraDevice cameraDevice)
//        {
//            cameraDevice.close();
//            MainActivity.this.cameraDevice = null;
//        }
//
//        // 打开摄像头出现错误时激发该方法
//        @Override
//        public void onError(CameraDevice cameraDevice, int error)
//        {
//            cameraDevice.close();
//            MainActivity.this.cameraDevice = null;
//            MainActivity.this.finish();
//        }
//    };
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    @Override
//    protected void onCreate(Bundle savedInstanceState)
//    {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        rootLayout = findViewById(R.id.root);
//        requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0x123);
//
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions, @NonNull int[] grantResults)
//    {
//        if (requestCode == 0x123 && grantResults.length == 4
//                && grantResults[0] == PackageManager.PERMISSION_GRANTED
//        ) {
//            // 创建预览摄像头图片的TextureView组件
//            textureView = new AutoFitTextureView(MainActivity.this, null);
//            // 为TextureView组件设置监听器
//            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
//            rootLayout.addView(textureView);
//            record=findViewById(R.id.capture);
//            record.setOnClickListener(view -> startRecordingVideo());
//            stop=findViewById(R.id.stop);
//            stop.setOnClickListener(view -> stopRecordingVideo());
//            stop.setEnabled(false);
//        }
//    }
//
//
//    private void setUpMediaRecorder(int width, int height) throws IOException {
//        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            // 获取指定摄像头的特性
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
//            // 获取摄像头支持的配置属性
//            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.
//                    SCALER_STREAM_CONFIGURATION_MAP);
//            // 获取摄像头支持的最大尺寸
//            Size largest = Collections.max(
//                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
//                    new CompareSizesByArea());
//            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
//            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                    width, height, mVideoSize);
//
//            // 获取最佳的预览尺寸
//            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
//                    width, height, largest);
//            // 根据选中的预览尺寸来调整预览组件（TextureView的）的长宽比
//            int orientation = getResources().getConfiguration().orientation;
//            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
//            } else {
//                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
//            }
//            SetpMediaRecorder();
//        }
//        catch (CameraAccessException e)
//        {
//            e.printStackTrace();
//        }
//        catch (NullPointerException e)
//        {
//            System.out.println("出现错误。");
//        }
//
//    }
//    private void SetpMediaRecorder(){
//        try {
//            String mNextVideoAbsolutePath="";
//            File externalStorageDirectory = Environment.getExternalStorageDirectory();
//            File dir = new File(externalStorageDirectory, "_MyVideo");
//            System.out.println("sd read-->" + dir.canRead());
//            System.out.println("sd write-->" + dir.canWrite());
//            if (!dir.exists()) {
//                dir.mkdir();
//            }
//            mNextVideoAbsolutePath=dir.getAbsolutePath() + "/video_"+ System.currentTimeMillis() + ".mp4";
//            mMediaRecorder = new MediaRecorder();
//
//
//
//            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//
//            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//
//
//            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//            mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
//            mMediaRecorder.setVideoFrameRate(30);
//            mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
//            mMediaRecorder.setVideoEncodingBitRate(10000000);
//
//
//            mMediaRecorder.prepare();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//    private Size chooseVideoSize(Size[] choices) {
//        for (Size size : choices) {
//            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
//                Log.i(TAG, "chooseVideoSize: " + size.toString());
//                return size;
//            }
//        }
//        Log.e(TAG, "Couldn't find any suitable video size");
//        return choices[choices.length - 1];
//    }
//
//    private void stopRecordingVideo() {
//        try {
//            mMediaRecorder.setOnErrorListener(null);
//            mMediaRecorder.stop();
//
//        } catch (RuntimeException stopException) {
//        }
//        mMediaRecorder.release();
//        mMediaRecorder=null;
//        Toast.makeText(this, "录制视频已保存", Toast.LENGTH_LONG).show();
//        record.setEnabled(true);
//        stop.setEnabled(false);
//        createCameraPreviewSession();
//    }
//    private void updatePreview() {
//        if (null == cameraDevice) {
//            return;
//        }
//        try {
//            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//    private void startRecordingVideo() {
//        if (null == cameraDevice || !textureView.isAvailable() || null == mPreviewSize) {
//            return;
//        }
//
//        try {
//
//            SurfaceTexture texture = textureView.getSurfaceTexture();
//            assert texture != null;
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//            List<Surface> surfaces = new ArrayList<>();
//
//            Surface previewSurface = new Surface(texture);
//            surfaces.add(previewSurface);
//            if(mMediaRecorder==null)
//            {
//                SetpMediaRecorder();
//            }
//
//            Surface recorderSurface = mMediaRecorder.getSurface();
//            surfaces.add(recorderSurface);
//            captureRequestBuilder.addTarget(recorderSurface);
//            previewRequestBuilder.addTarget(recorderSurface);
//
//            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession CaptureSession) {
//                    captureSession=CaptureSession;
//                    updatePreview();
//                    mMediaRecorder.start();
//                    record.setEnabled(false);
//                    stop.setEnabled(true);
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    Toast.makeText(MainActivity.this, "配置失败！",
//                            Toast.LENGTH_SHORT).show();
//                }
//            }, null);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//
//    // 根据手机的旋转方向确定预览图像的方向
//    private void configureTransform(int viewWidth, int viewHeight) {
//        if (null == previewSize) {
//            return;
//        }
//        // 获取手机的旋转方向
//        int rotation = getWindowManager().getDefaultDisplay().getRotation();
//        Matrix matrix = new Matrix();
//        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
//        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
//        float centerX = viewRect.centerX();
//        float centerY = viewRect.centerY();
//        // 处理手机横屏的情况
//        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
//            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
//            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
//            float scale = Math.max(
//                    (float) viewHeight / previewSize.getHeight(),
//                    (float) viewWidth / previewSize.getWidth());
//            matrix.postScale(scale, scale, centerX, centerY);
//            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
//        }
//        // 处理手机倒置的情况
//        else if (Surface.ROTATION_180 == rotation)
//        {
//            matrix.postRotate(180, centerX, centerY);
//        }
//        textureView.setTransform(matrix);
//    }
//    // 打开摄像头
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private void openCamera(int width, int height)
//    {
//        //表示输出设置（拍照后的保存设置）
//        try {
//            setUpMediaRecorder(width, height);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        configureTransform(width, height);
//        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            // 如果用户没有授权使用摄像头，直接返回
//            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//                return;
//            }
//            // 打开摄像头
//            manager.openCamera(mCameraId, stateCallback, null); // ①
//        }
//        catch (CameraAccessException e)
//        {
//            e.printStackTrace();
//        }
//    }
//    private void createCameraPreviewSession()
//    {
//        try
//        {
//            SurfaceTexture texture = textureView.getSurfaceTexture();
//            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
//            Surface surface = new Surface(texture);
//            // 创建作为预览的CaptureRequest.Builder
//            previewRequestBuilder = cameraDevice
//                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            // 将textureView的surface作为CaptureRequest.Builder的目标
//
//            //给此次请求添加一个Surface对象作为图像的输出目标，
//            // CameraDevice返回的数据送到这个target surface中
//            previewRequestBuilder.addTarget(surface);
//            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
//            //第一个参数是一个数组，表示对相机捕获到的数据进行处理的相关容器组
//            //第二个参数是状态回调
//            //第三个参数设置线程
//            cameraDevice.createCaptureSession(Arrays.asList(surface),
//                    new CameraCaptureSession.StateCallback() // ③
//                    {
//                        //完成配置时回调，可以开始拍照或预览、录像
//                        @Override
//                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
//                        {
//                            // 如果摄像头为null，直接结束方法
//                            if (null == cameraDevice)
//                            {
//                                return;
//                            }
//                            // 当摄像头已经准备好时，开始显示预览
//                            captureSession = cameraCaptureSession;
//                            // 设置自动对焦模式
//                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                            // 设置自动曝光模式
//                            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
//                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                            // 开始显示相机预览
//                            previewRequest = previewRequestBuilder.build();
//                            try {
//                                // 设置预览时连续捕获图像数据
//                                captureSession.setRepeatingRequest(previewRequest, null, null);  // ④
//                            }
//                            catch (CameraAccessException e)
//                            {
//                                e.printStackTrace();
//                            }
//                        }
//                        @Override public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
//                        {
//                            Toast.makeText(MainActivity.this, "配置失败！",
//                                    Toast.LENGTH_SHORT).show();
//                        }
//                    }, null);
//        }
//        catch (CameraAccessException e)
//        {
//            e.printStackTrace();
//        }
//    }
//    private static Size chooseOptimalSize(Size[] choices
//            , int width, int height, Size aspectRatio)
//    {
//        // 收集摄像头支持的打过预览Surface的分辨率
//        List<Size> bigEnough = new ArrayList<>();
//        int w = aspectRatio.getWidth();
//        int h = aspectRatio.getHeight();
//        for (Size option : choices)
//        {
//            if (option.getHeight() == option.getWidth() * h / w &&
//                    option.getWidth() >= width && option.getHeight() >= height)
//            {
//                bigEnough.add(option);
//            }
//        }
//        // 如果找到多个预览尺寸，获取其中面积最小的。
//        if (bigEnough.size() > 0)
//        {
//            return Collections.min(bigEnough, new CompareSizesByArea());
//        }
//        else
//        {
//            System.out.println("找不到合适的预览尺寸！！！");
//            return choices[0];
//        }
//    }
//    // 为Size定义一个比较器Comparator
//    static class CompareSizesByArea implements Comparator<Size>
//    {
//        @Override
//        public int compare(Size lhs, Size rhs)
//        {
//            // 强转为long保证不会发生溢出
//            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
//                    (long) rhs.getWidth() * rhs.getHeight());
//        }
//    }
//}
