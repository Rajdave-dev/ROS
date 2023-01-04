package com.raj.ros;

import android.os.Environment;
import androidx.core.os.EnvironmentCompat;
import com.raj.ros.*;
import java.io.File;
import java.nio.file.ReadOnlyFileSystemException;

public class Variables {
    
//STRINGS
    public static final String PACKAGE_NAME=BuildConfig.APPLICATION_ID;
    public static final String DATA_DIR="/data/data/"+PACKAGE_NAME;
    
//ROOTFS SUBDIRS PATH
    public static final String ROOTFS_DIR=DATA_DIR+"/rootfs";
    public static final String ROOTFS_ROS_DIR=ROOTFS_DIR+"/ROS";
    public static final String ROS_SYSTEM_DIR=ROOTFS_ROS_DIR+"/System";
    public static final String LINUX_DIR=ROS_SYSTEM_DIR+"/linux";
    public static final String SYSTEM_EXEC_DIR=ROS_SYSTEM_DIR+"/bin";
    public static final String EXTERNAL_PUBLIC_DIRECTORY=Environment.getExternalStorageDirectory().getAbsolutePath();
    public static final String SYSTEM_LIBS_DIR=ROS_SYSTEM_DIR+"/libexec";
    public static final String SYSTEM_TMP_DIR=ROS_SYSTEM_DIR+"/tmp";
    public static final String VISIBLE_TO_DOCS=LINUX_DIR;
    public static final String ICONS_FOR_APP_DIR=ROS_SYSTEM_DIR+"/icons";
    public static final String WALLPAPER_DIR=ROS_SYSTEM_DIR+"/wallpapers";
    
    public static final String getCurrentHome(){ 
        return VISIBLE_TO_DOCS+"/root"; 
    }
}
