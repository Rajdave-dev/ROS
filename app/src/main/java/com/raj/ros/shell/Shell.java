package com.raj.ros.shell;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.raj.ros.shell.*;

@SuppressWarnings("unused")
public class Shell {

  static final String[] AVAILABLE_TEST_COMMANDS = new String[]{"echo -BOC-", "id"};

  @WorkerThread
  public static CommandResult run(@NonNull String shell, @NonNull String... commands) {
    return run(shell, commands, null);
  }

  
  @WorkerThread
  public static CommandResult run(@NonNull String shell, @NonNull String[] commands, @Nullable String[] env) {
    List<String> stdout = Collections.synchronizedList(new ArrayList<String>());
    List<String> stderr = Collections.synchronizedList(new ArrayList<String>());
    int exitCode;

    try {
      // setup our process, retrieve stdin stream, and stdout/stderr gobblers
      Process process = runWithEnv(shell, env);
      DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
      StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), stdout);
      StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), stderr);

      
      stdoutGobbler.start();
      stderrGobbler.start();
      try {
        for (String write : commands) {
          stdin.write((write + "\n").getBytes("UTF-8"));
          stdin.flush();
        }
        stdin.write("exit\n".getBytes("UTF-8"));
        stdin.flush();
      } catch (IOException e) {
        //noinspection StatementWithEmptyBody
        if (e.getMessage().contains("EPIPE")) {
        } else {
          throw e;
        }
      }

      exitCode = process.waitFor();

     try {
        stdin.close();
      } catch (IOException e) {
      }
      stdoutGobbler.join();
      stderrGobbler.join();
      process.destroy();
    } catch (InterruptedException e) {
      exitCode = ShellExitCode.WATCHDOG_EXIT;
    } catch (IOException e) {
      exitCode = ShellExitCode.SHELL_WRONG_UID;
    }

    return new CommandResult(stdout, stderr, exitCode);
  }


  @WorkerThread
  public static Process runWithEnv(@NonNull String command, @Nullable String[] environment) throws IOException {
    if (environment != null) {
      Map<String, String> newEnvironment = new HashMap<>();
      newEnvironment.putAll(System.getenv());
      int split;
      for (String entry : environment) {
        if ((split = entry.indexOf("=")) >= 0) {
          newEnvironment.put(entry.substring(0, split), entry.substring(split + 1));
        }
      }
      int i = 0;
      environment = new String[newEnvironment.size()];
      for (Map.Entry<String, String> entry : newEnvironment.entrySet()) {
        environment[i] = entry.getKey() + "=" + entry.getValue();
        i++;
      }
    }
    return Runtime.getRuntime().exec(command, environment);
  }


  @WorkerThread
  public static Process runWithEnv(@NonNull String command, Map<String, String> environment) throws IOException {
    String[] env;
    if (environment != null && environment.size() != 0) {
      Map<String, String> newEnvironment = new HashMap<>();
      newEnvironment.putAll(System.getenv());
      newEnvironment.putAll(environment);
      int i = 0;
      env = new String[newEnvironment.size()];
      for (Map.Entry<String, String> entry : newEnvironment.entrySet()) {
        env[i] = entry.getKey() + "=" + entry.getValue();
        i++;
      }
    } else {
      env = null;
    }
    return Runtime.getRuntime().exec(command, env);
  }

  
  static boolean parseAvailableResult(List<String> stdout, boolean checkForRoot) {
    if (stdout == null) {
      return false;
    }

    // this is only one of many ways this can be done
    boolean echoSeen = false;

    for (String line : stdout) {
      if (line.contains("uid=")) {
        return !checkForRoot || line.contains("uid=0");
      } else if (line.contains("-BOC-")) {
        echoSeen = true;
      }
    }

    return echoSeen;
  }

  public static class SH {

    private static volatile Console console;

    @WorkerThread
    public static Console getConsole() throws ShellNotFoundException {
      if (console == null || console.isClosed()) {
        synchronized (SH.class) {
          if (console == null || console.isClosed()) {
            console = new Console.Builder().useSH().setWatchdogTimeout(30).build();
          }
        }
      }
      return console;
    }

    
    public static void closeConsole() {
      if (console != null) {
        synchronized (SH.class) {
          if (console != null) {
            console.close();
            console = null;
          }
        }
      }
    }

    
    @WorkerThread
    public static CommandResult run(@NonNull String... commands) {
      return Shell.run("sh", commands);
    }

  }

  
  public static class SU {

    private static Boolean isSELinuxEnforcing = null;
    private static String[] suVersion = new String[]{null, null};
    private static volatile Console console;

    
    @WorkerThread
    public static Console getConsole() throws ShellNotFoundException {
      if (console == null || console.isClosed()) {
        synchronized (SH.class) {
          if (console == null || console.isClosed()) {
            console = new Console.Builder().useSU().setWatchdogTimeout(30).build();
          }
        }
      }
      return console;
    }

    
    public static void closeConsole() {
      if (console != null) {
        synchronized (SU.class) {
          if (console != null) {
            console.close();
            console = null;
          }
        }
      }
    }

    @WorkerThread
    public static CommandResult run(@NonNull String... commands) {
      try {
        Console console = SU.getConsole();
        return console.run(commands);
      } catch (ShellNotFoundException e) {
        return new CommandResult(
            Collections.<String>emptyList(), Collections.<String>emptyList(), ShellExitCode.SHELL_NOT_FOUND);
      }
    }

    
    @WorkerThread
    public static boolean available() {
      CommandResult result = run(Shell.AVAILABLE_TEST_COMMANDS);
      return Shell.parseAvailableResult(result.stdout, true);
    }

    
    @WorkerThread
    public static synchronized String version(boolean internal) {
      int idx = internal ? 0 : 1;
      if (suVersion[idx] == null) {
        String version = null;

        CommandResult result = Shell.run(internal ? "su -V" : "su -v", "exit");

        for (String line : result.stdout) {
          if (!internal) {
            if (!line.trim().equals("")) {
              version = line;
              break;
            }
          } else {
            try {
              if (Integer.parseInt(line) > 0) {
                version = line;
                break;
              }
            } catch (NumberFormatException e) {
            }
          }
        }

        suVersion[idx] = version;
      }
      return suVersion[idx];
    }

    public static boolean isSU(@NonNull String shell) {
      
      int pos = shell.indexOf(' ');
      if (pos >= 0) {
        shell = shell.substring(0, pos);
      }

      pos = shell.lastIndexOf('/');
      if (pos >= 0) {
        shell = shell.substring(pos + 1);
      }

      return shell.equals("su");
    }

    public static String shell(int uid, @Nullable String context) {
      String shell = "su";

      if ((context != null) && isSELinuxEnforcing()) {
        String display = version(false);
        String internal = version(true);

        if ((display != null) &&
            (internal != null) &&
            (display.endsWith("SUPERSU")) &&
            (Integer.valueOf(internal) >= 190)) {
          shell = String.format(Locale.ENGLISH, "%s --context %s", shell, context);
        }
      }

      
      if (uid > 0) {
        shell = String.format(Locale.ENGLISH, "%s %d", shell, uid);
      }

      return shell;
    }

    
    public static String shellMountMaster() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        return "su --mount-master";
      }
      return "su";
    }

    @WorkerThread
    public static synchronized boolean isSELinuxEnforcing() {
      if (isSELinuxEnforcing == null) {
        Boolean enforcing = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          File f = new File("/sys/fs/selinux/enforce");
          if (f.exists()) {
            InputStream is = null;
            try {
              is = new FileInputStream("/sys/fs/selinux/enforce");
              enforcing = (is.read() == '1');
            } catch (Exception e) {
            } finally {
              if (is != null) {
                try {
                  is.close();
                } catch (IOException ignored) {
                }
              }
            }
          }

          if (enforcing == null) {
            try {
              Class<?> SELinux = Class.forName("android.os.SELinux");
              Method isSELinuxEnforced = SELinux.getMethod("isSELinuxEnforced");
              if (!isSELinuxEnforced.isAccessible()) isSELinuxEnforced.setAccessible(true);
              enforcing = (Boolean) isSELinuxEnforced.invoke(SELinux.newInstance());
            } catch (Exception e) {
              // 4.4+ release builds are enforcing by default, take the gamble
              enforcing = Build.VERSION.SDK_INT >= 19;
            }
          }
        }

        if (enforcing == null) {
          enforcing = false;
        }

        isSELinuxEnforcing = enforcing;
      }
      return isSELinuxEnforcing;
    }

    
    public static synchronized void clearCachedResults() {
      isSELinuxEnforcing = null;
      suVersion[0] = null;
      suVersion[1] = null;
    }

  }

  
  public interface OnCommandResultListener extends ShellExitCode {

    
    void onCommandResult(int commandCode, int exitCode, List<String> output);
  }

  
  public interface OnCommandLineListener extends ShellExitCode, StreamGobbler.OnLineListener {

    
    void onCommandResult(int commandCode, int exitCode);
  }

  
  private static class Command {

    private static int commandCounter = 0;

    private final String[] commands;
    private final int code;
    private final OnCommandResultListener onCommandResultListener;
    private final OnCommandLineListener onCommandLineListener;
    private final String marker;

    public Command(String[] commands, int code, OnCommandResultListener onCommandResultListener,
                   OnCommandLineListener onCommandLineListener) {
      this.commands = commands;
      this.code = code;
      this.onCommandResultListener = onCommandResultListener;
      this.onCommandLineListener = onCommandLineListener;
      this.marker = UUID.randomUUID().toString() + String.format("-%08x", ++commandCounter);
    }
  }

  
  public static class Builder {

    private Map<String, String> environment = new HashMap<>();
    private List<Command> commands = new LinkedList<>();
    private StreamGobbler.OnLineListener onStdoutLineListener;
    private StreamGobbler.OnLineListener onStderrLineListener;
    private Handler handler;
    private boolean autoHandler = true;
    private boolean wantStderr;
    private String shell = "sh";
    private int watchdogTimeout;


    public Builder setHandler(Handler handler) {
      this.handler = handler;
      return this;
    }


    public Builder setAutoHandler(boolean autoHandler) {
      this.autoHandler = autoHandler;
      return this;
    }


    public Builder setShell(String shell) {
      this.shell = shell;
      return this;
    }


    public Builder useSH() {
      return setShell("sh");
    }

    
    public Builder useSU() {
      return setShell("su");
    }

    
    public Builder setWantStderr(boolean wantStderr) {
      this.wantStderr = wantStderr;
      return this;
    }

    
    public Builder addEnvironment(String key, String value) {
      environment.put(key, value);
      return this;
    }

    
    public Builder addEnvironment(Map<String, String> addEnvironment) {
      environment.putAll(addEnvironment);
      return this;
    }

    
    public Builder addCommand(String command) {
      return addCommand(command, 0, null);
    }


    public Builder addCommand(String command, int code, OnCommandResultListener onCommandResultListener) {
      return addCommand(new String[]{
          command
      }, code, onCommandResultListener);
    }

    
    public Builder addCommand(List<String> commands) {
      return addCommand(commands, 0, null);
    }

    
    public Builder addCommand(List<String> commands, int code, OnCommandResultListener onCommandResultListener) {
      return addCommand(commands.toArray(new String[commands.size()]), code,
          onCommandResultListener);
    }

    
    public Builder addCommand(String[] commands) {
      return addCommand(commands, 0, null);
    }

    
    public Builder addCommand(String[] commands, int code, OnCommandResultListener onCommandResultListener) {
      this.commands.add(new Command(commands, code, onCommandResultListener, null));
      return this;
    }

    
    public Builder setOnStdoutLineListener(StreamGobbler.OnLineListener onLineListener) {
      this.onStdoutLineListener = onLineListener;
      return this;
    }

    
    public Builder setOnStderrLineListener(StreamGobbler.OnLineListener onLineListener) {
      this.onStderrLineListener = onLineListener;
      return this;
    }

    
    public Builder setWatchdogTimeout(int watchdogTimeout) {
      this.watchdogTimeout = watchdogTimeout;
      return this;
    }

    
    @WorkerThread
    public Interactive open() {
      return new Interactive(this, null);
    }

    
    @WorkerThread
    public Interactive open(OnCommandResultListener onCommandResultListener) {
      return new Interactive(this, onCommandResultListener);
    }
  }

  
  @SuppressWarnings("unused")
  public static class Interactive {

    private final Handler handler;
    private final boolean autoHandler;
    final String shell;
    final boolean wantSTDERR;
    private final List<Command> commands;
    private final Map<String, String> environment;
    final StreamGobbler.OnLineListener onStdoutLineListener;
    final StreamGobbler.OnLineListener onStderrLineListener;
    private final Object idleSync = new Object();
    private final Object callbackSync = new Object();

    volatile String lastMarkerStdout;
    volatile String lastMarkerStderr;
    volatile Command command;
    private volatile List<String> buffer;
    private volatile boolean running;
    private volatile boolean idle = true; // read/write only synchronized
    private volatile boolean closed = true;
    private volatile int callbacks;
    private volatile int watchdogCount;
    volatile int lastExitCode;

    private Process process;
    private DataOutputStream stdin;
    private StreamGobbler stdout;
    private StreamGobbler stderr;
    private ScheduledThreadPoolExecutor watchdog;
    int watchdogTimeout;

    
    Interactive(final Builder builder, final OnCommandResultListener onCommandResultListener) {
      autoHandler = builder.autoHandler;
      shell = builder.shell;
      wantSTDERR = builder.wantStderr;
      commands = builder.commands;
      environment = builder.environment;
      onStdoutLineListener = builder.onStdoutLineListener;
      onStderrLineListener = builder.onStderrLineListener;
      watchdogTimeout = builder.watchdogTimeout;

      if ((Looper.myLooper() != null) && (builder.handler == null) && autoHandler) {
        handler = new Handler();
      } else {
        handler = builder.handler;
      }

      if (onCommandResultListener != null) {
        watchdogTimeout = 60;
        commands.add(0, new Command(Shell.AVAILABLE_TEST_COMMANDS, 0, new OnCommandResultListener() {

          @Override public void onCommandResult(int commandCode, int exitCode, List<String> output) {
            if ((exitCode == ShellExitCode.SUCCESS) &&
                !Shell.parseAvailableResult(output, Shell.SU.isSU(shell))) {
              // shell is up, but it's brain-damaged
              exitCode = ShellExitCode.SHELL_EXEC_FAILED;
            }
            watchdogTimeout = builder.watchdogTimeout;
            onCommandResultListener.onCommandResult(0, exitCode, output);
          }
        }, null));
      }

      if (!open() && (onCommandResultListener != null)) {
        onCommandResultListener.onCommandResult(0, ShellExitCode.SHELL_WRONG_UID, null);
      }
    }

    
    public void addCommand(@NonNull String... commands) {
      addCommand(commands, 0, (OnCommandResultListener) null);
    }

    
    public void addCommand(@NonNull String command, int code, @Nullable OnCommandResultListener resultListener) {
      addCommand(new String[]{command}, code, resultListener);
    }

    
    public void addCommand(@NonNull String command, int code, @Nullable OnCommandLineListener onCommandLineListener) {
      addCommand(new String[]{command}, code, onCommandLineListener);
    }

    
    public void addCommand(@NonNull List<String> commands) {
      addCommand(commands, 0, (OnCommandResultListener) null);
    }

    
    public void addCommand(@NonNull List<String> commands, int code,
                           @Nullable OnCommandResultListener onCommandResultListener) {
      addCommand(commands.toArray(new String[commands.size()]), code, onCommandResultListener);
    }

    
    public void addCommand(@NonNull List<String> commands, int code,
                           @Nullable OnCommandLineListener lineListener) {
      addCommand(commands.toArray(new String[commands.size()]), code, lineListener);
    }

    
    public synchronized void addCommand(@NonNull String[] commands, int code,
                                        @Nullable OnCommandResultListener resultListener) {
      this.commands.add(new Command(commands, code, resultListener, null));
      runNextCommand();
    }

    
    public synchronized void addCommand(@NonNull String[] commands, int code,
                                        @Nullable OnCommandLineListener onCommandLineListener) {
      this.commands.add(new Command(commands, code, null, onCommandLineListener));
      runNextCommand();
    }

    
    private void runNextCommand() {
      runNextCommand(true);
    }

    synchronized void handleWatchdog() {
      final int exitCode;

      if (watchdog == null)
        return;
      if (watchdogTimeout == 0)
        return;

      if (!isRunning()) {
        exitCode = ShellExitCode.SHELL_DIED;
      } else if (watchdogCount++ < watchdogTimeout) {
        return;
      } else {
        exitCode = ShellExitCode.WATCHDOG_EXIT;
      }

      if (handler != null) {
        postCallback(command, exitCode, buffer);
      }

      // prevent multiple callbacks for the same command
      command = null;
      buffer = null;
      idle = true;

      watchdog.shutdown();
      watchdog = null;
      kill();
    }

    /**
     * Start the periodic timer when a command is submitted
     */
    private void startWatchdog() {
      if (watchdogTimeout == 0) {
        return;
      }
      watchdogCount = 0;
      watchdog = new ScheduledThreadPoolExecutor(1);
      watchdog.scheduleAtFixedRate(new Runnable() {

        @Override public void run() {
          handleWatchdog();
        }
      }, 1, 1, TimeUnit.SECONDS);
    }

    
    private void stopWatchdog() {
      if (watchdog != null) {
        watchdog.shutdownNow();
        watchdog = null;
      }
    }


    private void runNextCommand(boolean notifyIdle) {
      

      boolean running = isRunning();
      if (!running)
        idle = true;

      if (running && idle && (commands.size() > 0)) {
        Command command = commands.get(0);
        commands.remove(0);

        buffer = null;
        lastExitCode = 0;
        lastMarkerStdout = null;
        lastMarkerStderr = null;

        if (command.commands.length > 0) {
          try {
            if (command.onCommandResultListener != null) {
               buffer = Collections.synchronizedList(new ArrayList<String>());
            }

            idle = false;
            this.command = command;
            startWatchdog();
            for (String write : command.commands) {
              stdin.write((write + "\n").getBytes("UTF-8"));
            }
            stdin.write(("echo " + command.marker + " $?\n").getBytes("UTF-8"));
            stdin.write(("echo " + command.marker + " >&2\n").getBytes("UTF-8"));
            stdin.flush();
          } catch (IOException e) {
            // stdin might have closed
          }
        } else {
          runNextCommand(false);
        }
      } else if (!running) {
        // our shell died for unknown reasons - abort all submissions
        while (commands.size() > 0) {
          postCallback(commands.remove(0), ShellExitCode.SHELL_DIED, null);
        }
      }

      if (idle && notifyIdle) {
        synchronized (idleSync) {
          idleSync.notifyAll();
        }
      }
    }

    
    synchronized void processMarker() {
      if (command.marker.equals(lastMarkerStdout)
          && (command.marker.equals(lastMarkerStderr))) {
        postCallback(command, lastExitCode, buffer);
        stopWatchdog();
        command = null;
        buffer = null;
        idle = true;
        runNextCommand();
      }
    }

    
    synchronized void processLine(String line, StreamGobbler.OnLineListener listener) {
      if (listener != null) {
        if (handler != null) {
          final String fLine = line;
          final StreamGobbler.OnLineListener fListener = listener;

          startCallback();
          handler.post(new Runnable() {

            @Override public void run() {
              try {
                fListener.onLine(fLine);
              } finally {
                endCallback();
              }
            }
          });
        } else {
          listener.onLine(line);
        }
      }
    }


    synchronized void addBuffer(String line) {
      if (buffer != null) {
        buffer.add(line);
      }
    }

    private void startCallback() {
      synchronized (callbackSync) {
        callbacks++;
      }
    }

    
    private void postCallback(final Command fCommand, final int fExitCode, final List<String> fOutput) {
      if (fCommand.onCommandResultListener == null && fCommand.onCommandLineListener == null) {
        return;
      }
      if (handler == null/* || !handler.getLooper().getThread().isAlive()*/) {
        if ((fCommand.onCommandResultListener != null) && (fOutput != null))
          fCommand.onCommandResultListener.onCommandResult(fCommand.code, fExitCode, fOutput);
        if (fCommand.onCommandLineListener != null)
          fCommand.onCommandLineListener.onCommandResult(fCommand.code, fExitCode);
        return;
      }
      startCallback();

      handler.post(new Runnable() {

        @Override public void run() {
          try {
            if ((fCommand.onCommandResultListener != null) && (fOutput != null))
              fCommand.onCommandResultListener.onCommandResult(fCommand.code, fExitCode, fOutput);
            if (fCommand.onCommandLineListener != null)
              fCommand.onCommandLineListener.onCommandResult(fCommand.code, fExitCode);
          } finally {
            endCallback();
          }
        }
      });
    }

    /**
     * Decrease callback counter, signals callback complete state when dropped to 0
     */
    void endCallback() {
      synchronized (callbackSync) {
        callbacks--;
        if (callbacks == 0) {
          callbackSync.notifyAll();
        }
      }
    }

    private synchronized boolean open() {
      try {
        // setup our process, retrieve stdin stream, and stdout/stderr gobblers
        process = runWithEnv(shell, environment);
        stdin = new DataOutputStream(process.getOutputStream());
        stdout = new StreamGobbler(process.getInputStream(), new StreamGobbler.OnLineListener() {

          @Override public void onLine(String line) {
            synchronized (Interactive.this) {
              if (command == null) {
                return;
              }

              String contentPart = line;
              String markerPart = null;

              int markerIndex = line.indexOf(command.marker);
              if (markerIndex == 0) {
                contentPart = null;
                markerPart = line;
              } else if (markerIndex > 0) {
                contentPart = line.substring(0, markerIndex);
                markerPart = line.substring(markerIndex);
              }

              if (contentPart != null) {
                addBuffer(contentPart);
                processLine(contentPart, onStdoutLineListener);
                processLine(contentPart, command.onCommandLineListener);
              }

              if (markerPart != null) {
                try {
                  lastExitCode = Integer.valueOf(markerPart.substring(command.marker.length() + 1), 10);
                } catch (Exception e) {
                  // this really shouldn't happen
                  e.printStackTrace();
                }
                lastMarkerStdout = command.marker;
                processMarker();
              }
            }
          }
        });
        stderr = new StreamGobbler(process.getErrorStream(), new StreamGobbler.OnLineListener() {

          @Override public void onLine(String line) {
            synchronized (Interactive.this) {
              if (command == null) {
                return;
              }

              String contentPart = line;

              int markerIndex = line.indexOf(command.marker);
              if (markerIndex == 0) {
                contentPart = null;
              } else if (markerIndex > 0) {
                contentPart = line.substring(0, markerIndex);
              }

              if (contentPart != null) {
                if (wantSTDERR)
                  addBuffer(contentPart);
                processLine(contentPart, onStderrLineListener);
              }

              if (markerIndex >= 0) {
                lastMarkerStderr = command.marker;
                processMarker();
              }
            }
          }
        });

        stdout.start();
        stderr.start();

        running = true;
        closed = false;

        runNextCommand();

        return true;
      } catch (IOException e) {
        return false;
      }
    }

    
    public void close() {
      boolean idle = isIdle(); 

      synchronized (this) {
        if (!running)
          return;
        running = false;
        closed = true;
      }

      

      if (!idle)
        waitForIdle();

      try {
        try {
          stdin.write(("exit\n").getBytes("UTF-8"));
          stdin.flush();
        } catch (IOException e) {
          //noinspection StatementWithEmptyBody
          if (e.getMessage().contains("EPIPE")) {
          } else {
            throw e;
          }
        }

        process.waitFor();

        try {
          stdin.close();
        } catch (IOException e) {
        }
        stdout.join();
        stderr.join();
        stopWatchdog();
        process.destroy();
      } catch (InterruptedException | IOException e) {
        
      }
    }

    
    public synchronized void kill() {
      running = false;
      closed = true;

      try {
        stdin.close();
      } catch (IOException e) {
        
      }
      try {
        process.destroy();
      } catch (Exception e) {
        
      }
    }

    
    public boolean isRunning() {
      if (process == null) {
        return false;
      }
      try {
        process.exitValue();
        return false;
      } catch (IllegalThreadStateException e) {
        
      }
      return true;
    }

    
    public synchronized boolean isIdle() {
      if (!isRunning()) {
        idle = true;
        synchronized (idleSync) {
          idleSync.notifyAll();
        }
      }
      return idle;
    }

    
    public boolean waitForIdle() {
      if (isRunning()) {
        synchronized (idleSync) {
          while (!idle) {
            try {
              idleSync.wait();
            } catch (InterruptedException e) {
              return false;
            }
          }
        }

        if ((handler != null) &&
            (handler.getLooper() != null) &&
            (handler.getLooper() != Looper.myLooper())) {
          
          synchronized (callbackSync) {
            while (callbacks > 0) {
              try {
                callbackSync.wait();
              } catch (InterruptedException e) {
                return false;
              }
            }
          }
        }
      }

      return true;
    }

    
    public boolean hasHandler() {
      return (handler != null);
    }

  }

  
  public static class Console implements Closeable {

    private final OnCloseListener onCloseListener;
    private final Shell.Interactive shell;
    final HandlerThread callbackThread;
    private final boolean wantStderr;
    List<String> stdout;
    List<String> stderr;
    int exitCode;
    boolean isCommandRunning;
    private boolean closed;

    private final Shell.OnCommandResultListener commandResultListener = new Shell.OnCommandResultListener() {

      @Override public void onCommandResult(int commandCode, int exitCode, List<String> stdout) {
        Console.this.exitCode = exitCode;
        Console.this.stdout = stdout;
        synchronized (callbackThread) {
          isCommandRunning = false;
          callbackThread.notifyAll();
        }
      }
    };

    Console(Builder builder) throws ShellNotFoundException {
      try {
        onCloseListener = builder.onCloseListener;
        wantStderr = builder.wantStderr;

        callbackThread = new HandlerThread("Shell Callback");
        callbackThread.start();
        isCommandRunning = true;

        Shell.Builder shellBuilder = new Shell.Builder();
        shellBuilder.setShell(builder.shell);
        shellBuilder.setHandler(new Handler(callbackThread.getLooper()));
        shellBuilder.setWatchdogTimeout(builder.watchdogTimeout);
        shellBuilder.addEnvironment(builder.environment);
        shellBuilder.setWantStderr(false);

        if (builder.wantStderr) {
          shellBuilder.setOnStderrLineListener(new StreamGobbler.OnLineListener() {
            @Override public void onLine(String line) {
              if (stderr != null) {
                stderr.add(line);
              }
            }
          });
        }

        shell = shellBuilder.open(commandResultListener);

        waitForCommandFinished();
        if (exitCode != ShellExitCode.SUCCESS) {
          close();
          throw new ShellNotFoundException("Access was denied or this is not a shell");
        }
      } catch (Exception e) {
        throw new ShellNotFoundException("Error opening shell '" + builder.shell + "'", e);
      }
    }

    
    @WorkerThread
    public synchronized CommandResult run(String... commands) {
      isCommandRunning = true;
      if (wantStderr) {
        stderr = Collections.synchronizedList(new ArrayList<String>());
      } else {
        stderr = Collections.emptyList();
      }
      shell.addCommand(commands, 0, commandResultListener);
      waitForCommandFinished();
      CommandResult result = new CommandResult(stdout, stderr, exitCode);
      stderr = null;
      stdout = null;
      return result;
    }

    
    @Override public synchronized void close() {
      try {
        shell.close();
      } catch (Exception ignored) {
      }
      synchronized (callbackThread) {
        callbackThread.notifyAll();
      }
      callbackThread.interrupt();
      callbackThread.quit();
      closed = true;
      if (onCloseListener != null) {
        onCloseListener.onClosed(this);
      }
    }


    public boolean isClosed() {
      return closed;
    }

    private void waitForCommandFinished() {
      synchronized (callbackThread) {
        while (isCommandRunning) {
          try {
            callbackThread.wait();
          } catch (InterruptedException ignored) {
          }
        }
      }
      if (exitCode == ShellExitCode.WATCHDOG_EXIT || exitCode == ShellExitCode.SHELL_DIED) {
        close();
      }
    }

    
    public interface OnCloseListener {


      void onClosed(Console console);
    }

    
    public static class Builder {

      Console.OnCloseListener onCloseListener;
      Map<String, String> environment = new HashMap<>();
      String shell = "sh";
      boolean wantStderr = true;
      int watchdogTimeout;

      
      public Builder setShell(String shell) {
        this.shell = shell;
        return this;
      }

      
      public Builder useSH() {
        return setShell("sh");
      }

      
      public Builder useSU() {
        return setShell("su");
      }

      
      public Builder setWantStderr(boolean wantStderr) {
        this.wantStderr = wantStderr;
        return this;
      }


      public Builder setWatchdogTimeout(int watchdogTimeout) {
        this.watchdogTimeout = watchdogTimeout;
        return this;
      }


      public Builder addEnvironment(String key, String value) {
        environment.put(key, value);
        return this;
      }

      
      public Builder addEnvironment(Map<String, String> addEnvironment) {
        environment.putAll(addEnvironment);
        return this;
      }

      public Builder setOnCloseListener(Console.OnCloseListener onCloseListener) {
        this.onCloseListener = onCloseListener;
        return this;
      }

      public Console build() throws ShellNotFoundException {
        return new Console(this);
      }

    }

  }

}