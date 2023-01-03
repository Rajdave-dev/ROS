package com.raj.ros;
import android.app.Application;
import cat.ereza.customactivityoncrash.config.CaocConfig;
import com.raj.ros.CrashHandler;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CaocConfig.Builder.create()
        .backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM)
        .enabled(true)
        .showErrorDetails(true)
        .showRestartButton(true)
        .logErrorOnRestart(true)
        .trackActivities(false)
        .errorActivity(CrashHandler.class)
        .apply();
    }
}
