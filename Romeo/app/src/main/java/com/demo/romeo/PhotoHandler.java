package com.demo.romeo;

/**
 * Created by Felix on 3/11/17.
 */

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PhotoHandler implements PictureCallback {

    private final static String DEBUG_TAG = "PhotoHandler";
    private final Context mContext;
    private final PictureFileHandler mPictureFileHandler;

    public PhotoHandler(Context context, PictureFileHandler pictureFileHandler) {
        mContext = context;
        mPictureFileHandler = pictureFileHandler;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {

        try {

            // Get target directory
            File pictureFileDir = getDir();

            // If target directory doesn't exist - create it
            if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
                Log.d(DEBUG_TAG, "Can't create directory to save image.");
                Toast.makeText(mContext, "Can't create directory to save image.",
                        Toast.LENGTH_LONG).show();
                return;

            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyymmddhhmmss");
            String date = dateFormat.format(new Date());
            String photoFile = "Picture_" + date + ".jpg";

            String filename = pictureFileDir.getPath() + File.separator + photoFile;

            File pictureFile = new File(filename);

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                Toast.makeText(mContext, "New Image saved:" + photoFile,
                        Toast.LENGTH_LONG).show();

                // Call to picture file handler for further processing
                mPictureFileHandler.handlePictureFile(pictureFile);
            } catch (Exception error) {
                Log.d(DEBUG_TAG, "File" + filename + "not saved: "
                        + error.getMessage());
                Toast.makeText(mContext, "Image could not be saved.",
                        Toast.LENGTH_LONG).show();
            }
        }
        finally {
            mPictureFileHandler.pictureReceived();
        }
    }

    private File getDir() {
        File sdDir = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(sdDir, "RemotePhotography");
    }
}