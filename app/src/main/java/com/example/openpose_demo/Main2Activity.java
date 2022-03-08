package com.example.openpose_demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;


public class Main2Activity extends AppCompatActivity implements View.OnClickListener {

    private AutoFitTextureView mTextureview;

    private Button mTakePictureBtn;//拍照

    private Button mVideoRecodeBtn;//开始录像
    private LinearLayout mVerticalLinear;
    private CameraController mCameraController;
    private boolean mIsRecordingVideo; //开始停止录像
    public static String BASE_PATH = Environment.getExternalStorageDirectory() + "/OpenposeVideo";
    public static String video_path=null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        setContentView(R.layout.activity_main2);
        checkPermission();


    }



    @Override
    protected void onResume() {
        super.onResume();
        //获取相机管理类的实例
        mCameraController = CameraController.getmInstance(this);
        mCameraController.setFolderPath(BASE_PATH);


        initView();
        //判断当前横竖屏状态

    }

    private void initView() {
        mTextureview = (AutoFitTextureView) findViewById(R.id.textureview);
        mTakePictureBtn = (Button) findViewById(R.id.process);
        mTakePictureBtn.setOnClickListener(this);
        mVideoRecodeBtn = (Button) findViewById(R.id.video_recode_btn);
        mVideoRecodeBtn.setOnClickListener(this);
        mVerticalLinear = (LinearLayout) findViewById(R.id.vertical_linear);



        //判断当前屏幕方向
        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            //竖屏时
            mVerticalLinear.setVisibility(View.VISIBLE);
        } else {
            //横屏时
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            mVerticalLinear.setVisibility(View.VISIBLE);
        }
        mCameraController.InitCamera(mTextureview);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
            case R.id.process:
                if(video_path!=null){
                    Intent intent=new Intent(Main2Activity.this,Openpose.class);
                    Bundle bundle=new Bundle();
                    bundle.putCharSequence("BASE_PATH",BASE_PATH);
                    bundle.putCharSequence("video_path",video_path);
                    intent.putExtras(bundle);
                    startActivity(intent);

                } else {
                    Toast.makeText(this, "请先录制视频", Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.video_recode_btn:
                if (mIsRecordingVideo) {
                    mIsRecordingVideo = !mIsRecordingVideo;
                    mCameraController.stopRecordingVideo();
                    mVideoRecodeBtn.setText("开始录像");
                    video_path=mCameraController.getVideoPath();
                    Toast.makeText(this, "录像结束", Toast.LENGTH_SHORT).show();
                } else {
                    mVideoRecodeBtn.setText("停止录像");

                    mIsRecordingVideo = !mIsRecordingVideo;
                    mCameraController.startRecordingVideo();
                    Toast.makeText(this, "录像开始", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS}; // 选择你需要申请的权限
            for (int i = 0; i < permissions.length; i++) {
                int state = ContextCompat.checkSelfPermission(this, permissions[i]);
                if (state != PackageManager.PERMISSION_GRANTED) { // 判断权限的状态
                    ActivityCompat.requestPermissions(this, permissions, 200); // 申请权限
                    return;
                }
            }
        }
    }

}