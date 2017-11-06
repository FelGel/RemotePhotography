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
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class MainActivity extends Activity implements TriggerInterface, PictureFileHandler {
    private final static String DEBUG_TAG = "MainActivity";

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private Context mContext;
    private Camera mCamera;
    private SurfaceView mSurfaceViewDummy;
    private int mCameraId = 0;
    private boolean mIsConnectedToCamera = false;
    private ExecutorService mExecutorService;
    private PhotoHandler mPhotoHandler;
    private Button mTakePictureButton;
    private TextView mIpAddressTextView;
    private TextView mStatusTextView;
    private SimpleWebServer mServer;
    private Semaphore mCameraLock = new Semaphore(1, true);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mPhotoHandler = new PhotoHandler(getApplicationContext(), this);

        mSurfaceViewDummy = (SurfaceView) findViewById(R.id.surfaceView);
        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        mIpAddressTextView = (TextView) findViewById(R.id.ipAddressTextView);
        mTakePictureButton = (Button) findViewById(R.id.takePictureButton);
        mTakePictureButton.setAlpha(0.1f);
        mTakePictureButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.d(DEBUG_TAG, "onClick called");
                takeAndEmailPicture();
            }
        });

        mServer = new SimpleWebServer(55555, this);
        requestPermissions();

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Init executor service
        mExecutorService = Executors.newFixedThreadPool(2);
    }

    @Override
    public void onResume(){
        super.onResume();
        Log.d(DEBUG_TAG, "onResume called");

        // Start HTTP server (if not running already)
        if (!mServer.isRunning()) {
            String ipAddress = wifiIpAddress(mContext);
            if (ipAddress == null || ipAddress.length() == 0) {
                Log.e(DEBUG_TAG, "Failed to find WIFI IP address");
                //mIpAddressTextView.setText("Wifi is connection required");
            } else {
                Log.d(DEBUG_TAG, "Ip address:" + ipAddress);
                mIpAddressTextView.setText("Server IP: " + ipAddress);
            }

            mServer.start();
            mStatusTextView.setText("Server started. Waiting for incoming requests");
        }

//        try {
//            FirebaseInstanceId.getInstance().deleteInstanceId();
//        } catch (Exception e) {
//
//        }
        FirebaseMessaging.getInstance().subscribeToTopic("test_topic");

    }

    // Will be called when HTTP request is received
    public void trigger() {
        Log.i(DEBUG_TAG, "Remote trigger received");
        takeAndEmailPicture();
    }

    public void pictureReceived() {
        releaseCamera();
        mCameraLock.release();
        Log.d(DEBUG_TAG, "Camera lock released");
    }


    public void handlePictureFile(final File attachment) {
        Log.d(DEBUG_TAG, "handlePictureFile called");

        mExecutorService.execute(new Runnable() {
            public void run() {
                try {
                    Log.i(DEBUG_TAG, "Sending email");
                    updateStatus("Sending email");

                    GMailSender sender = new GMailSender("remote.photography.demo@gmail.com", "darktower");
                    sender.sendMail("RemotePhotography picture",
                            "See attachment for picture",
                            "remote.photography.demo@gmail.com",
                            "remote.photography.demo@gmail.com",
                            attachment);

                    Log.i(DEBUG_TAG, "Email sent");
                    updateStatus("Email sent");

                } catch (Exception e) {
                    updateStatus("Failed to send email");
                    Log.e("SendMail", e.getMessage(), e);
                }
            }
        });
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

    private boolean connectToCamera() {

        // If already connected, release first
        if (mIsConnectedToCamera) {
            releaseCamera();
        }

        // do we have a mCamera?
        if (mCameraId < 0) {
            if (!getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                updateStatus("No camera on this device");
                Toast.makeText(this, "No mCamera on this device", Toast.LENGTH_LONG)
                        .show();
                return false;
            }

            // Get camera ID
            mCameraId = findCamera();
        }
        if (mCameraId < 0) {
            updateStatus("No camera found");
            Toast.makeText(this, "No Camera found.",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Init camera
        mCamera = Camera.open(mCameraId);
        try {
            mCamera.setPreviewDisplay(mSurfaceViewDummy.getHolder());
            mCamera.startPreview();
            mIsConnectedToCamera = true;
            updateStatus("Connected to camera");
            Log.d(DEBUG_TAG, "Connected to camera");
            return true;
        } catch (IOException e) {
            updateStatus("Failed to connected to camera");
            e.printStackTrace();
        }

        return false;
    }

    private int findCamera() {
        int cameraId = -1;

        // Search for mCamera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                updateStatus("Camera found");
                Log.d(DEBUG_TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private void releaseCamera() {
        if (mCamera != null && mIsConnectedToCamera) {
            mCamera.stopPreview();
            mCamera.release();
            mIsConnectedToCamera = false;
        }
    }

    private void takeAndEmailPicture() {

        // Run in a separate thread
        mExecutorService.execute(new Runnable() {
            public void run() {
                Log.d(DEBUG_TAG, "Acquiring camera lock");
                try {
                    mCameraLock.acquire();
                } catch (Exception ex) {
                    Log.d(DEBUG_TAG, "Lock exception");
                    return;
                }

                Log.d(DEBUG_TAG, "Camera lock acquired");

                // Connect to camera
                if (!connectToCamera()) {
                    updateStatus("Failed to connect to the camera");
                    Log.d(DEBUG_TAG, "Not connected to mCamera");
                    return;
                }

                Log.i(DEBUG_TAG, "Taking picture");
                updateStatus("Taking picture");
                try {
                    mCamera.takePicture(null, null, mPhotoHandler);
                    Log.i(DEBUG_TAG, "Picture taken successfully");
                } catch (Exception e) {
                    Log.e(DEBUG_TAG, "Failed to take picture");
                }
            }
        });
    }

    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
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
            Log.e(DEBUG_TAG, "Unable to get host address");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(DEBUG_TAG, "OnStop called");

        // Stop HTTP server
        mServer.stop();

        // Shutdown executor service
        mExecutorService.shutdown();
        mExecutorService.shutdownNow();

        // Release camera
        releaseCamera();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(DEBUG_TAG, "onDestroy called");
    }
}
