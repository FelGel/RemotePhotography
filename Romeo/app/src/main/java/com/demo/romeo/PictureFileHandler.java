package com.demo.romeo;

import java.io.File;

/**
 * Created by Felix on 3/11/17.
 */

public interface PictureFileHandler {
    void handlePictureFile(File attachment);
    void pictureReceived();
}
