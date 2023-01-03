package com.raj.ros;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.system.Os;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.app.*;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textview.MaterialTextView;
import com.raj.ros.InstallerActivity;
import com.raj.ros.Variables;
import com.raj.ros.helper.Helper;
import com.raj.ros.terminal.TerminalActivity;
import com.raj.ros.BuildConfig;
import com.raj.ros.shell.Shell;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class InstallerActivity extends AppCompatActivity {
    
    private boolean needLinux=false;
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helper.hideSystemUI(getWindow());
		setContentView(R.layout.activity_installer);
        MaterialTextView textView = findViewById(R.id.output);
        textView.append("ROS Installer Version "+BuildConfig.VERSION_NAME+"Starting Linux (Currently only Ubuntu 22.10) installation.");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        needLinux=true;
        install();
    }
    
    void install(){
        installRap();
    }
    
     static void deleteFolder(File fileOrDirectory) throws IOException {
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
    
    void installRap(){
        new Thread() {
            @Override
            public void run() {
                try {
                    final String STAGING_PREFIX_PATH = Variables.ROS_SYSTEM_DIR;
                    final File STAGING_PREFIX_FILE = new File(STAGING_PREFIX_PATH);

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);
                    try (ZipInputStream zipInput = new ZipInputStream(InstallerActivity.this.getResources().openRawResource(R.raw.bootstrap))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = symlinksReader.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2)
                                        throw new RuntimeException("Malformed symlink line: " + line);
                                    String oldPath = parts[0];
                                    String newPath = STAGING_PREFIX_PATH + "/" + parts[1];
                                    symlinks.add(Pair.create(oldPath, newPath));

                                    ensureDirectoryExists(new File(newPath).getParentFile());
                                }
                            } else {
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
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") || zipEntryName.startsWith("lib/apt/methods")) {
                                        //noinspection OctalInteger
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }

                    if (symlinks.isEmpty())
                        throw new RuntimeException("No SYMLINKS.txt encountered");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }
                    Helper.installROOTFS(InstallerActivity.this,R.raw.rootfs);
                    Helper.installROOTFS(InstallerActivity.this,R.raw.patch);
                    Shell.SH.run(new String[]{"."+Variables.SYSTEM_EXEC_DIR+"/busybox --install -s " + Variables.SYSTEM_EXEC_DIR});
                    startActivity(new Intent(InstallerActivity.this,TerminalActivity.class));
                    finish();
                } catch (final Exception e) {
                }
            }
        }.start();
    }
    
    private static void ensureDirectoryExists(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + directory.getAbsolutePath());
        }
    }
}
