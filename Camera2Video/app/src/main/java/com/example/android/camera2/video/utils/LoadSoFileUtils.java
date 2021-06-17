package com.example.android.camera2.video.utils;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * so The library is copied from sd card to the private directory of app, and compared, verified and loaded
 */

public class LoadSoFileUtils {
    private static final String nameSO = "libmms_api";

    /**
     * Load so file
     */
    public static int loadSoFile(Context context, String fromPath) {
        File dir = context.getDir("jniLibs", Context.MODE_PRIVATE);
        String fromPathFile = fromPath + "/" + nameSO + ".so";
        File isExist = new File(fromPathFile);
        if (isExist.exists()) {
            isExist.delete();
        }
        return copy(fromPath, dir.getAbsolutePath());
    }

    /**
     * Determine whether so file exists
     */
    public static boolean isLoadSoFile(File dir, Boolean isExist) {
        File[] currentFiles;
        currentFiles = dir.listFiles();
        boolean hasSoLib = false;
        if (currentFiles == null) {
            return false;
        }
        for (File currentFile : currentFiles) {
            //Determine whether there is a library in it, and if sd also has a library, delete it, and then copy it outside
            if (currentFile.getName().contains(nameSO)) {
                hasSoLib = isExist && !currentFile.delete();
            }
        }
        return hasSoLib;
    }

    public static int copy(String fromFile, String toFile) {
        //Directory of files to copy
        File[] currentFiles;
        File root = new File(fromFile);
        //It's like judging whether the SD card exists or whether the file exists. If it doesn't exist, return it
        if (!root.exists()) {
            return -1;
        }
        //If it exists, get all the files in the current directory to fill the array
        currentFiles = root.listFiles();

        if (currentFiles == null) {
            return -1;
        }
        //Target directory
        File targetDir = new File(toFile);
        //Create directory
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        int statue = -1;
        //Traverse all files in the directory to be copied
        for (File currentFile : currentFiles) {
            if (currentFile.isDirectory()) {
                //If the current item is a subdirectory for recursion
                copy(currentFile.getPath() + "/", toFile + currentFile.getName() + "/");
            } else {
                //Copy the file if the current item is a file
                if (currentFile.getName().contains(".so")) {
                    statue = copySdcardFile(currentFile.getPath(), toFile + File.separator + currentFile.getName());
                }
            }
        }
        return statue;
    }


    //File copy
    //Copies of all non subdirectory (folder) files in the directory to be copied
    public static int copySdcardFile(String fromFile, String toFile) {
        try {
            FileInputStream fosfrom = new FileInputStream(fromFile);
            FileOutputStream fosto = new FileOutputStream(toFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = -1;
            while ((len = fosfrom.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            // From memory to write to specific file
            fosto.write(baos.toByteArray());
            // Close file stream
            baos.close();
            fosto.close();
            fosfrom.close();
            return 0;
        } catch (Exception ex) {
            return -1;
        }
    }
}