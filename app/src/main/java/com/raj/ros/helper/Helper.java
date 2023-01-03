package com.raj.ros.helper;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.system.Os;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.raj.ros.*;
import com.raj.ros.helper.Helper;
import com.raj.ros.shell.Shell;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONException;
import org.json.JSONObject;

public class Helper {
    
    static boolean isLSd = true;
    
    public static void hideSystemUI(Window window) {
      WindowInsetsControllerCompat windowInsetsController =ViewCompat.getWindowInsetsController(window.getDecorView());
      if (windowInsetsController == null) {
          return; 
       }
       windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
       windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }
    public static void dialog(String title,String msg, Context c){
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(c);
        b.setTitle(title);
        b.setMessage(msg);
        b.setCancelable(true);
        b.create().show();
     }
     public static void shortToast(Context c,Object msg){
         Toast.makeText(c,msg.toString(),Toast.LENGTH_SHORT).show();
     }
     public static long getTotalRam(Context c){
         ActivityManager actManager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
         ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
         actManager.getMemoryInfo(memInfo);
         return memInfo.totalMem;
     }
     public static void installROOTFS(final Activity c,int raw){
         try {
             final String STAGING_PREFIX_PATH = Variables.DATA_DIR;
             final File STAGING_PREFIX_FILE = new File(Variables.ROOTFS_DIR);
             final byte[] buffer = new byte[8096];
              ZipInputStream zipInput = new ZipInputStream(c.getResources().openRawResource(raw));
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                                String zipEntryName = zipEntry.getName();
                                File targetFile = new File(STAGING_PREFIX_PATH, zipEntryName);
                                boolean isDirectory = zipEntry.isDirectory();
                                ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1)
                                            outStream.write(buffer, 0, readBytes);
                                    }
                                    if(targetFile.getName().equals("busybox")){
                                        Shell.SH.run(new String[]{"chmod 755 "+targetFile.getAbsolutePath()});
                                        Shell.SH.run(new String[]{"."+targetFile.getAbsolutePath()+" --install -c " + Variables.SYSTEM_EXEC_DIR});
                                    }
                                    if(targetFile.getAbsolutePath().contains("bin")){
                                        Shell.SH.run(new String[]{"chmod 755 "+targetFile.getAbsolutePath()});
                                    }
                                    if(targetFile.getAbsolutePath().contains("proot")){
                                        Shell.SH.run(new String[]{"chmod 755 "+targetFile.getAbsolutePath()});
                                    }
                                    if(targetFile.getAbsolutePath().contains("libexec")){
                                        Shell.SH.run(new String[]{"chmod 755 "+targetFile.getAbsolutePath()});
                                    }
                                }
                        }
          } catch (final IOException e) {
          dialog("installerror",e.toString(),c);
          } finally {
          }
     }
     
     private static void ensureDirectoryExists(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + directory.getAbsolutePath());
        }
    }
    public static void deleteFolder(File fileOrDirectory) throws IOException {
        if (fileOrDirectory.getCanonicalPath().equals(fileOrDirectory.getAbsolutePath()) && fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteFolder(child);
                }
            }
        }
        if (!fileOrDirectory.delete()) {
            throw new RuntimeException("Unable to delete " + (fileOrDirectory.isDirectory() ? "directory " : "file ") + fileOrDirectory.getAbsolutePath());
        }
    }
}