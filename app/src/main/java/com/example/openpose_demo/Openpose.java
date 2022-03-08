package com.example.openpose_demo;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import static org.opencv.core.CvType.CV_32FC3;
import static org.opencv.core.CvType.CV_8UC3;

public class Openpose extends AppCompatActivity implements View.OnClickListener{
    static final String TAG = "PythonOnAndroid";
    public static final String 请先处理视频 = "请先处理视频";
    private Python py;
    public String Image_PATH;
    private String new_str;
    private Button button,button2;
    private String mp4_str;
    private ProgressDialog dialog;
    private Module module = null;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_openpose);
        Intent accept=getIntent();
        Bundle bundle=accept.getExtras();
        mp4_str=bundle.getString("video_path");
        Image_PATH=bundle.getString("BASE_PATH")+"/ProcessedVideo";
        checkPermission();
        initPython();
        py = Python.getInstance();


        try {
            module = Module.load(assetFilePath(this, "20201120model.pt"));
        } catch (IOException e) {
            Log.e("PytorchHelloWorld", "Error reading assets", e);
            finish();
        }
//        String path= null; //替换

//        try {
//            path = assetFilePath(this, "body_pose_model.pth");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        py.getModule("openpose").callAttr("model_process",path);
        setFolderPath(Image_PATH);

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        initView();

    }

    private void initView() {
        dialog=new ProgressDialog(this);
        dialog.setTitle("提示信息");
        dialog.setMessage("正在处理，请稍后...");
        dialog.setCanceledOnTouchOutside(false);
        button = (Button) findViewById(R.id.play);
        button.setOnClickListener(this);
        button2 = (Button) findViewById(R.id.img_process);
        button2.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            default:
                break;
            case R.id.play:
                if(new_str!=null){
                    play_mp4();
                }else{
                    Toast.makeText(this, 请先处理视频,Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.img_process:
                new imgTask().execute();
//                processing();
                break;
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
    }



    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
//                    mOpenCvCameraView.enableView();
//                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void play_mp4(){
        String type = "video/*";
        File apkFile = new File(new_str);
        if (!apkFile.exists()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri contentUri = FileProvider.getUriForFile(this, "com.rfid.application.MyApplication.provider", apkFile);

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(contentUri, type);

        }else{
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");

        }


        this.startActivity(intent);


    }

    private void processing(){
        Mat imgMat=new Mat();
        int fram_num=0;
        int time=0;

        int fps = 4;
        int ifourcc = VideoWriter.fourcc('M','J','P','G');
        org.opencv.core.Size cvSize = new org.opencv.core.Size(480,640);
        Mat frame=new Mat(640,480,CV_32FC3);
        Mat outImg=new Mat();
        Bitmap bitmap=null;


        new_str=Image_PATH+"/"+System.currentTimeMillis() + ""
                + new Random().nextInt(1000000) + ".avi";
        VideoWriter writer = new VideoWriter(new_str, ifourcc, fps, cvSize);

//        if(writer.isOpened()) {
//            System.out.println("zzzz122222222222222222"+new_str);
//            Toast.makeText(getApplicationContext(), "successful！！！", Toast.LENGTH_SHORT).show();
//        }
//        else {
//            Toast.makeText(getApplicationContext(),"The opening fail！！！",Toast.LENGTH_SHORT).show();
//        }
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
//        mmr.setDataSource("/storage/emulated/0/Download/111.mp4");
        mmr.setDataSource(mp4_str);
        // 播放时长单位为毫秒
        String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        try {
            time= Integer.parseInt(duration);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        fram_num =time/250;
//        long startTime = System.currentTimeMillis();    //获取开始时间

        for(int i=0;i<fram_num-1;i++){
//            long startTime = System.currentTimeMillis();    //获取开始时间
//            long startTime2 = System.currentTimeMillis();    //获取开始时间

//            bitmap=mmr.getFrameAtIndex(i*5);
            bitmap=mmr.getFrameAtTime((i*250*1000),MediaMetadataRetriever.OPTION_CLOSEST);
//            long endTime2 = System.currentTimeMillis();    //获取结束时间
//            System.out.println("第一段：" + (endTime2 - startTime2) + "ms"+"      "+i);    //输出程序运行时间

            Utils.bitmapToMat(bitmap,imgMat);

            Imgproc.cvtColor(imgMat,imgMat,Imgproc.COLOR_RGBA2BGR);
            imgMat.convertTo(imgMat,CV_32FC3,1.0/255,0);

            int length=imgMat.cols() * imgMat.rows() *imgMat.channels();
            float[] frameArray = new float[length];
            imgMat.get(0,0,frameArray);



            PyObject obj = py.getModule("Handle123").callAttr("image", frameArray);


//            long startTime3 = System.currentTimeMillis();    //获取开始时间


            float[] input = obj.toJava(float[].class);
            final Tensor inputTensor=Tensor.fromBlob(input,new long[]{1,3,184,144});
            IValue outputTensor = module.forward(IValue.from(inputTensor));
            Tensor out1=outputTensor.toTuple()[0].toTensor();
            Tensor out2=outputTensor.toTuple()[1].toTensor();
            float[] Mconv7_stage6_L1=out1.getDataAsFloatArray();
            float[] Mconv7_stage6_L2=out2.getDataAsFloatArray();

//            long endTime3 = System.currentTimeMillis();    //获取结束时间
//            System.out.println("第二段：" + (endTime3 - startTime3) + "ms"+"      "+i);    //输出程序运行时间

//            long startTime4 = System.currentTimeMillis();    //获取开始时间


            PyObject obj1=py.getModule("Handle123").callAttr("openpose_imge", Mconv7_stage6_L1,Mconv7_stage6_L2);

//            long endTime4 = System.currentTimeMillis();    //获取结束时间
//            System.out.println("第三段：" + (endTime4 - startTime4) + "ms"+"      "+i);    //输出程序运行时间


            float[] out=obj1.toJava(float[].class);

            frame.put(0,0,out);

            frame.convertTo(outImg,CV_8UC3,255);
            writer.write(outImg);//matVideo是该类的成员变量，在此之前已经缓存了Mat视频帧信息。
//            long endTime = System.currentTimeMillis();    //获取结束时间
//            System.out.println("程序运行时间：" + (endTime - startTime) + "ms"+"      "+i);    //输出程序运行时间
        }
//        long endTime = System.currentTimeMillis();    //获取结束时间
//        System.out.println("程序运行时间：" + (endTime - startTime) + "ms");    //输出程序运行时间

        mmr.release();
        writer.release();

//        Mat imgMat=new Mat();int fram_num=0;
//        int time=0;
//
//        int fps = 1;
//        int ifourcc = VideoWriter.fourcc('M','J','P','G');
//        org.opencv.core.Size cvSize = new org.opencv.core.Size(480,640);
//        Mat frame=new Mat(640,480,CV_32FC3);
//        Mat outImg=new Mat();
//        Bitmap bitmap=null;
//
//
//        new_str=Image_PATH+"/"+System.currentTimeMillis() + ""
//                + new Random().nextInt(1000000) + ".avi";
//        VideoWriter writer = new VideoWriter(new_str, ifourcc, fps, cvSize);
//
////        if(writer.isOpened()) {
////            System.out.println("zzzz122222222222222222"+new_str);
////            Toast.makeText(getApplicationContext(), "successful！！！", Toast.LENGTH_SHORT).show();
////        }
////        else {
////            Toast.makeText(getApplicationContext(),"The opening fail！！！",Toast.LENGTH_SHORT).show();
////        }
//        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
//        mmr.setDataSource(mp4_str);
//        // 播放时长单位为毫秒
//        String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
//        try {
//            time= Integer.parseInt(duration);
//        } catch (NumberFormatException e) {
//            e.printStackTrace();
//        }
//        fram_num =time/1000;
//
//        for(int i=0;i<fram_num-1;i++){
//            bitmap=mmr.getFrameAtTime((i*1000*1000),MediaMetadataRetriever.OPTION_CLOSEST);
//            System.out.println("xxxxxxxxxxxxxxx          "+i);
//            Utils.bitmapToMat(bitmap,imgMat);
//
//            Imgproc.cvtColor(imgMat,imgMat,Imgproc.COLOR_RGBA2BGR);
//            imgMat.convertTo(imgMat,CV_32FC3,1.0/255,0);
//
//            int length=imgMat.cols() * imgMat.rows() *imgMat.channels();
//            float[] frameArray = new float[length];
//            imgMat.get(0,0,frameArray);
//
//            PyObject obj1=py.getModule("openpose").callAttr("img_process", frameArray);
//            float[] out=obj1.toJava(float[].class);
//
//            frame.put(0,0,out);
//
//            frame.convertTo(outImg,CV_8UC3,255);
//            writer.write(outImg);//matVideo是该类的成员变量，在此之前已经缓存了Mat视频帧信息。
//
//        }
//        mmr.release();
//        writer.release();

    }
    /*
     * 多线程处理图片
     *
     * */
    private class imgTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog.show();
        }


        @Override
        protected Void doInBackground(Void... voids) {
            processing();
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }
        @Override
        protected void onPostExecute(Void aLong) {
            super.onPostExecute(aLong);
            dialog.dismiss();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }


    void initPython(){
        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
    }




    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE}; // 选择你需要申请的权限
            for (int i = 0; i < permissions.length; i++) {
                int state = ContextCompat.checkSelfPermission(this, permissions[i]);
                if (state != PackageManager.PERMISSION_GRANTED) { // 判断权限的状态
                    ActivityCompat.requestPermissions(this, permissions, 200); // 申请权限
                    return;
                }
            }
        }
    }



    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            System.out.println(file.getAbsolutePath());
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public void setFolderPath(String path) {

        File mFolder = new File(path);
        if (!mFolder.exists()) {
            mFolder.mkdirs();
            Log.d(TAG, "文件夹不存在去创建");
            if (mFolder.exists()){
                Log.d(TAG, "文件夹创建成功");
            }
            else {
                Log.d(TAG, "文件夹创建失败");
            }
        } else {
            Log.d(TAG, "文件夹已创建");
        }
    }


}

