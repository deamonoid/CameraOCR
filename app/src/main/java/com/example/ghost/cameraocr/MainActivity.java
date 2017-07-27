package com.example.ghost.cameraocr;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Bitmap resizedBitmap;
    String datapath = "";
    private TessBaseAPI mTess;
    private Camera mCamera;
    private CameraPreview mPreview;
    private FrameLayout cameraPreviewLayout;
    private ImageView capturedImageHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Ask for Permission
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}
                , 101);

        //Immersive Mode

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);


        //initializing Tesseract API
        String language = "eng";
        datapath = getFilesDir() + "/tesseract/";
        mTess = new TessBaseAPI();

        checkFile(new File(datapath + "tessdata/"));

        mTess.init(datapath, language);
    }

    //Permission

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 101: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    cameraHandler();
                    camerAutoFocus();

                } else {

                    Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private void cameraHandler() {


        //Camera Handler
        cameraPreviewLayout = (FrameLayout) findViewById(R.id.camera_preview);
        capturedImageHolder = (ImageView) findViewById(R.id.captured_image);
        final Button OCRButton = (Button) findViewById(R.id.OCRbutton);
        final Button captureButton = (Button) findViewById(R.id.button_capture);

        mCamera = getCameraInstance();
        mPreview = new CameraPreview(this, mCamera);
        cameraPreviewLayout.addView(mPreview);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                captureButton.performClick();
            }
        }, 300);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                OCRButton.performClick();
            }
        }, 700);

        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Handler handler = new Handler();
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                mCamera.startPreview();
                                mCamera.setPreviewCallback(mPicture);
                                handler.postDelayed(this, 200);
                            }
                        };
                        handler.postDelayed(runnable, 200);
                    }
                }
        );


        OCRButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View V) {
                        final Handler handler = new Handler();
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                processImage();
                                handler.postDelayed(this, 1000);
                            }
                        };
                        handler.postDelayed(runnable, 1000);
                    }
                }
        );

    }

    private void camerAutoFocus() {

        //AutoFocus
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        mCamera.setParameters(parameters);

    }

    //Check if this device has a camera
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static android.hardware.Camera getCameraInstance() {
        android.hardware.Camera c = null;
        try {
            c = android.hardware.Camera.open();
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c;
    }

    Camera.PreviewCallback mPicture = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;

            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
            Rect rect = new Rect(0, 0, width, height);
            YuvImage yuvimage = new YuvImage(bytes,
                    ImageFormat.NV21, width, height, null);

            yuvimage.compressToJpeg(rect, 100, outstr);
            Bitmap bmp = BitmapFactory.decodeByteArray(
                    outstr.toByteArray(), 0, outstr.size());

            capturedImageHolder.setImageBitmap(scaleDownBitmapImage(bmp, 300, 200));

        }
    };

    private Bitmap scaleDownBitmapImage(Bitmap bitmap, int newWidth, int newHeight) {
        resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        return resizedBitmap;
    }

    //Tesseract Code
    private void checkFile(File dir) {
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles();
        }
        if (dir.exists()) {
            String datafilepath = datapath + "/tessdata/eng.traineddata";
            File datafile = new File(datafilepath);

            if (!datafile.exists()) {
                copyFiles();
            }
        }
    }

    private void copyFiles() {
        try {
            String filepath = datapath + "/tessdata/eng.traineddata";
            AssetManager assetManager = getAssets();

            InputStream instream = assetManager.open("tessdata/eng.traineddata");
            OutputStream outstream = new FileOutputStream(filepath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }


            outstream.flush();
            outstream.close();
            instream.close();

            File file = new File(filepath);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Processing Image to OCR
    public void processImage() {

        String OCRresult = null;
        mTess.setImage(resizedBitmap);
        OCRresult = mTess.getUTF8Text();
        TextView OCRTextView = (TextView) findViewById(R.id.OCRTextView);
        OCRTextView.setText(OCRresult);
    }
}