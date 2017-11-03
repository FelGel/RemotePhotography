package com.demo.romeo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TriggerInterface{
    protected final static String DEBUG_TAG = "MainActivity";

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private Context context;
    private Camera mCamera;
    private SurfaceView mSurfaceViewDummy;
    private int mCameraId = 0;
    private boolean mIsConnectedToCamera = false;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private PhotoHandler mPhotoHandler;
    private Button mTakePictureButton;
    private TextView mIpAddressTextView;
    private TextView mStatusTextView;
    private SimpleWebServer mServer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        mPhotoHandler = new PhotoHandler(getApplicationContext());
        mSurfaceViewDummy = (SurfaceView) findViewById(R.id.surfaceView);

        String ipAddress = wifiIpAddress(context);
        Log.d(DEBUG_TAG, "Ip address:" + ipAddress);
        mIpAddressTextView = (TextView) findViewById(R.id.ipAddress);
        mIpAddressTextView.setText("Server IP: " + ipAddress);

        mStatusTextView = (TextView) findViewById(R.id.status);

        mTakePictureButton = (Button) findViewById(R.id.capture);
        mTakePictureButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Log.d(DEBUG_TAG, "onClick called");
                takeAndEmailPicture();
            }

        });

        requestPermissions();

        mServer = new SimpleWebServer(55555, this);
        mServer.start();
        mStatusTextView.setText("Server started. Waiting for incoming requests");
    }

    public void trigger() {
        Log.d(DEBUG_TAG, "Trigger received");
        takeAndEmailPicture();
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean askForPermissions = false;
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                askForPermissions = true;
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                askForPermissions = true;
            }

            if (askForPermissions) {
                requestPermissions(new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        },
                        PERMISSIONS_REQUEST_CODE);
            }
        }
    }

    private void connectToCamera() {

        // do we have a mCamera?
        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            updateStatus("No camera on this device");
            Toast.makeText(this, "No mCamera on this device", Toast.LENGTH_LONG)
                    .show();
        } else {
            mCameraId = findCamera();
            if (mCameraId < 0) {
                updateStatus("No camera found");
                Toast.makeText(this, "No mCamera found.",
                        Toast.LENGTH_LONG).show();
            } else {
                mCamera = Camera.open(mCameraId);
                try {
                    mCamera.setPreviewDisplay(mSurfaceViewDummy.getHolder());
                    mIsConnectedToCamera = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void takeAndEmailPicture() {
        if (!mIsConnectedToCamera) {
            connectToCamera();
        }

        if (mIsConnectedToCamera) {
            mExecutorService.execute(new Runnable() {
                public void run() {

                    Log.d(DEBUG_TAG, "Taking picture");
                    updateStatus("Taking picture");
                    mCamera.startPreview();
                    mCamera.takePicture(null, null, new PhotoHandler(getApplicationContext()));
                    mCamera.stopPreview();
                    Log.d(DEBUG_TAG, "Picture taken successfully");

                    try {
                        Log.d(DEBUG_TAG,"Sending email");
                        updateStatus("Sending email");

                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) +
                                File.separator + "Screenshots", "1.png");

                        GMailSender sender = new GMailSender("remote.photography.demo@gmail.com", "darktower");
                        sender.sendMail("RemotePhotography picture",
                                "See picture in the attachment",
                                "remote.photography.demo@gmail.com",
                                "remote.photography.demo@gmail.com",
                                file);

                        Log.d(DEBUG_TAG,"Email sent");
                        updateStatus("Email sent");

                    } catch (Exception e) {
                        updateStatus("Failed to send email");
                        Log.e("SendMail", e.getMessage(), e);
                    }
                }
            });
        } else {
            updateStatus("Failed to connect to the camera");
            Log.d(DEBUG_TAG, "Not connected to mCamera");
        }
    }

    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
    }

    private int findCamera() {
        int cameraId = -1;

        // Search for mCamera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                updateStatus("Camera not found");
                Log.d(DEBUG_TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            return;
        }

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "mCamera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "mCamera permission denied", Toast.LENGTH_LONG).show();
            }
            if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "storage permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "storage permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    @Override
    protected void onPause() {
        if (mCamera != null && mIsConnectedToCamera) {
            mCamera.release();
            mIsConnectedToCamera = false;
        }
        super.onPause();
    }
}
