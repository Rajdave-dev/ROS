package com.raj.ros.terminal;

import android.util.Log;

import com.raj.ros.Variables;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BackgroundJob {

    private static final String LOG_TAG = "ros-terminal";

    final Process mProcess;

    public BackgroundJob(String cwd, String fileToExecute, final String[] args, final TerminalService service) {
        String[] env = buildEnvironment(false, cwd);
        if (cwd == null) cwd = service.homePath;

        final String[] progArray = setupProcessArgs(fileToExecute, args);
        final String processDescription = Arrays.toString(progArray);

        Process process;
        try {
            process = Runtime.getRuntime().exec(progArray, env, new File(cwd));
        } catch (IOException e) {
            mProcess = null;
            return;
        }

        mProcess = process;
        final int pid = getPid(mProcess);

        new Thread() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "[" + pid + "] starting: " + processDescription);
                InputStream stdout = mProcess.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
                String line;
                //TODO REPLACE LOGGER
                try {
                    while ((line = reader.readLine()) != null) {
                    }
                } catch (IOException e) {
                }

                try {
                    int exitCode = mProcess.waitFor();
                    service.onBackgroundJobExited(BackgroundJob.this);
                    if (exitCode == 0) {
                      //  Log.i(LOG_TAG, "[" + pid + "] exited normally");
                    } else {
                     //   Log.w(LOG_TAG, "[" + pid + "] exited with code: " + exitCode);
                    }
                } catch (InterruptedException e) {
                }
            }
        }.start();


        new Thread() {
            @Override
            public void run() {
                InputStream stderr = mProcess.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8));
                String line;
                try {
                    // FIXME: Long lines.
                    while ((line = reader.readLine()) != null) {
                        Log.i(LOG_TAG, "[" + pid + "] stderr: " + line);
                    }
                } catch (IOException e) {
                    // Ignore.
                }
            }
        };
    }


    public static String[] buildEnvironment(boolean failSafe, String cwd) {
        new File(Variables.getCurrentHome()).mkdirs();

        if (cwd == null) cwd = Variables.getCurrentHome();
        final String termEnv = "TERM=xterm-256color";
        final String homeEnv = "HOME="+Variables.getCurrentHome();
        final String prefixEnv = "PREFIX="+Variables.ROS_SYSTEM_DIR;//Variables.LINUX_DIR+"/usr";
        final String androidRootEnv = "ANDROID_ROOT=" + System.getenv("ANDROID_ROOT");
        final String androidDataEnv = "ANDROID_DATA=" + System.getenv("ANDROID_DATA");
        final String externalStorageEnv = "EXTERNAL_STORAGE=" + System.getenv("EXTERNAL_STORAGE");
        if (failSafe) {
            final String pathEnv = "PATH=" + System.getenv("PATH");
            return new String[]{termEnv, homeEnv, prefixEnv, androidRootEnv, androidDataEnv, pathEnv, externalStorageEnv};
        } else {
            final String ldEnv = "LD_LIBRARY_PATH="+Variables.SYSTEM_LIBS_DIR;//Variables.LINUX_DIR+"/usr/lib";
            final String langEnv = "LANG=en_US.UTF-8";
            final String pathEnv = "PATH="+ Variables.SYSTEM_EXEC_DIR;//+Variables.LINUX_DIR+"/usr/bin:"+Variables.LINUX_DIR+"/usr/bin/applets";
            final String pwdEnv = "PWD=" + Variables.getCurrentHome();
            final String tmpdirEnv = "TMPDIR=" + Variables.SYSTEM_TMP_DIR;
            final String prootTmp = "PROOT_TMP_DIR=" + Variables.SYSTEM_TMP_DIR;
            //PROOT_TMP_DIR
            return new String[]{termEnv, prootTmp, homeEnv, prefixEnv, ldEnv, langEnv, pathEnv, pwdEnv, androidRootEnv, androidDataEnv, externalStorageEnv, tmpdirEnv};
        }
    }

    public static int getPid(Process p) {
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            try {
                return f.getInt(p);
            } finally {
                f.setAccessible(false);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    static String[] setupProcessArgs(String fileToExecute, String[] args) {
      String interpreter = null;
        try {
            File file = new File(fileToExecute);
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[256];
                int bytesRead = in.read(buffer);
                if (bytesRead > 4) {
                    if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                    } else if (buffer[0] == '#' && buffer[1] == '!') {
                        // Try to parse shebang.
                        StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            char c = (char) buffer[i];
                            if (c == ' ' || c == '\n') {
                                if (builder.length() == 0) {
                                } else {
                                    String executable = builder.toString();
                                    if (executable.startsWith("/usr") || executable.startsWith("/bin")) {
                                        String[] parts = executable.split("/");
                                        String binary = parts[parts.length - 1];
                                        interpreter = Variables.LINUX_DIR + "/usr/bin/" + binary;
                                    }
                                    break;
                                }
                            } else {
                                builder.append(c);
                            }
                        }
                    } else {
                        interpreter = Variables.SYSTEM_EXEC_DIR+"/sh";
                    }
                }
            }
        } catch (IOException e) {
        }
        List<String> result = new ArrayList<>();
        if (interpreter != null) result.add(interpreter);
        result.add(fileToExecute);
        if (args != null) Collections.addAll(result, args);
        return result.toArray(new String[0]);
    }

}
