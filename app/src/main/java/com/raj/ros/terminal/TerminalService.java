package com.raj.ros.terminal;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.raj.ros.R;
import com.raj.ros.Variables;
import com.raj.ros.terminal.BackgroundJob;
import com.raj.ros.terminal.EmulatorDebug;
import com.raj.ros.terminal.TerminalSession;
import com.raj.ros.terminal.TerminalSession.SessionChangedCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class TerminalService extends Service implements SessionChangedCallback {

    private String TAG = "TerminalService";

    private static final String NOTIFICATION_CHANNEL_ID = "UserLAnd";

    
    @SuppressLint("SdCardPath")
    public String filesPath;
    public String supportPath;
    public String prefixPath;
    public String homePath;

    private static final int NOTIFICATION_ID = 2000;

    private static final String ACTION_STOP_SERVICE = "com.Terminal.service_stop";
    private static final String ACTION_LOCK_WAKE = "com.Terminal.service_wake_lock";
    private static final String ACTION_UNLOCK_WAKE = "com.Terminal.service_wake_unlock";

    public static final String ACTION_EXECUTE = "android.intent.action.EXECUTE";

    
    class LocalBinder extends Binder {
        public final TerminalService service = TerminalService.this;
    }

    private final IBinder mBinder = new LocalBinder();

    private final Handler mHandler = new Handler();

    
    final List<TerminalSession> mTerminalSessions = new ArrayList<>();

    final List<BackgroundJob> mBackgroundTasks = new ArrayList<>();

    
    SessionChangedCallback mSessionChangeCallback;

    
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;

    
    boolean mWantsToStop = false;

    @SuppressLint("Wakelock")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_STOP_SERVICE.equals(action)) {
            mWantsToStop = true;
            for (int i = 0; i < mTerminalSessions.size(); i++)
                mTerminalSessions.get(i).finishIfRunning();
            stopSelf();
        } else if (ACTION_LOCK_WAKE.equals(action)) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":" + EmulatorDebug.LOG_TAG);
                mWakeLock.acquire();
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG + ":" + EmulatorDebug.LOG_TAG);
                mWifiLock.acquire();
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String packageName = getPackageName();
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        Intent whitelist = new Intent();
                        whitelist.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        whitelist.setData(Uri.parse("package:" + packageName));
                        whitelist.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(whitelist);
                        } catch (ActivityNotFoundException e) {
                        }
                    }
                }
             }
            updateNotification();
        } else if (ACTION_UNLOCK_WAKE.equals(action)) {
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;

                mWifiLock.release();
                mWifiLock = null;
            }
            updateNotification();
        } else if (ACTION_EXECUTE.equals(action)) {
           
        } else if (action != null) {
            Log.e(EmulatorDebug.LOG_TAG, "Unknown TerminalService action: '" + action + "'");
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        filesPath = this.getFilesDir().getAbsolutePath();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":" + EmulatorDebug.LOG_TAG);
                mWakeLock.acquire();
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG + ":" + EmulatorDebug.LOG_TAG);
                mWifiLock.acquire();
        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());       
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();

        stopForeground(true);
        stopSelf();

        for (int i = 0; i < mTerminalSessions.size(); i++)
            mTerminalSessions.get(i).finishIfRunning();
    }

    public List<TerminalSession> getSessions() {
        return mTerminalSessions;
    }

    public boolean isWakelockEnabled() {
        if (mWakeLock == null) {
            return false;
        } else {
            return mWakeLock.isHeld();
        }
    }
    
    TerminalSession createTermSession(String executablePath, String[] arguments, String cwd, boolean failSafe) {
        new File(Variables.getCurrentHome()).mkdirs();

        if (cwd == null) cwd = Variables.getCurrentHome();

        String[] env = BackgroundJob.buildEnvironment(failSafe, cwd);
        boolean isLoginShell = false;
        if((new File(Variables.SYSTEM_EXEC_DIR+"/runLinux")).exists()) executablePath = Variables.SYSTEM_EXEC_DIR+"/runLinux";
        else executablePath = Variables.SYSTEM_EXEC_DIR+"/sh"; 
        if((new File(Variables.LINUX_DIR+"/rootfs")).exists()) executablePath = Variables.SYSTEM_EXEC_DIR+"/installLinux";
        if((new File(Variables.LINUX_DIR+"/.done")).exists()) executablePath = Variables.SYSTEM_EXEC_DIR+"/runLinux";

        String[] processArgs = BackgroundJob.setupProcessArgs(executablePath, arguments);// new String[]{"-0"});
        executablePath = processArgs[0];
        int lastSlashIndex = executablePath.lastIndexOf('/');
        String processName = (isLoginShell ? "-" : "") +
            (lastSlashIndex == -1 ? executablePath : executablePath.substring(lastSlashIndex + 1));

        String[] args = new String[processArgs.length];
        args[0] = processName;
        if (processArgs.length > 1) System.arraycopy(processArgs, 1, args, 1, processArgs.length - 1);

        TerminalSession session = new TerminalSession(executablePath, cwd, args, env, this);
        mTerminalSessions.add(session);
        updateNotification();
        return session;
    }
    
    void updateNotification() {
        if (mWakeLock == null && mTerminalSessions.isEmpty() && mBackgroundTasks.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            stopSelf();
        } else {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent notifyIntent = new Intent(this, TerminalActivity.class);
        // PendingIntent#getActivity(): "Note that the activity will be started outside of the context of an existing
        // activity, so you must use the Intent.FLAG_ACTIVITY_NEW_TASK launch flag in the Intent":
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);

        int sessionCount = mTerminalSessions.size();
        int taskCount = mBackgroundTasks.size();
        String contentText = sessionCount + " session" + (sessionCount == 1 ? "" : "s");
        if (taskCount > 0) {
            contentText += ", " + taskCount + " task" + (taskCount == 1 ? "" : "s");
        }
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(getText(R.string.app_name));
        builder.setContentText(contentText);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentIntent(pendingIntent);
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);
        builder.setShowWhen(false);
        builder.setColor(0xFF607D8B);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID);
        }
        Resources res = getResources();
        Intent exitIntent = new Intent(this, TerminalService.class).setAction(ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit), PendingIntent.getService(this, 0, exitIntent, 0));
        return builder.build();
    }


    public int removeTermSession(TerminalSession sessionToRemove) {
        int indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove);
        mTerminalSessions.remove(indexOfRemoved);
        if (mTerminalSessions.isEmpty()){ //&& mWakeLock == null) {
            stopSelf();
        }else{
        updateNotification();
        }
        return indexOfRemoved;
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTitleChanged(changedSession);
    }

    @Override
    public void onSessionFinished(final TerminalSession finishedSession) {
        if (mSessionChangeCallback != null)
            mSessionChangeCallback.onSessionFinished(finishedSession);
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTextChanged(changedSession);
    }

    @Override
    public void onClipboardText(TerminalSession session, String text) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onClipboardText(session, text);
    }

    @Override
    public void onBell(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onBell(session);
    }

    @Override
    public void onColorsChanged(TerminalSession session) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onColorsChanged(session);
    }

    public void onBackgroundJobExited(final BackgroundJob task) {
        mHandler.post(() -> {
            mBackgroundTasks.remove(task);
            updateNotification();
        });
    }
    
    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String channelName = "ROS";
        String channelDescription = "ROS TERMINAL";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName,importance);
        channel.setDescription(channelDescription);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
