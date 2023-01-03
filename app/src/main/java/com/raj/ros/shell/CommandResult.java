package com.raj.ros.shell;

import androidx.annotation.NonNull;

import java.util.List;


public class CommandResult implements ShellExitCode {

  private static String toString(List<String> lines) {
    StringBuilder sb = new StringBuilder();
    if (lines != null) {
      String emptyOrNewLine = "";
      for (String line : lines) {
        sb.append(emptyOrNewLine).append(line);
        emptyOrNewLine = "\n";
      }
    }
    return sb.toString();
  }

  @NonNull public final List<String> stdout;
  @NonNull public final List<String> stderr;
  public final int exitCode;

  public CommandResult(@NonNull List<String> stdout, @NonNull List<String> stderr, int exitCode) {
    this.stdout = stdout;
    this.stderr = stderr;
    this.exitCode = exitCode;
  }

  
  public boolean isSuccessful() {
    return exitCode == SUCCESS;
  }

  
  public String getStdout() {
    return toString(stdout);
  }

  public String getStderr() {
    return toString(stderr);
  }

  @Override public String toString() {
    return getStdout();
  }

}
