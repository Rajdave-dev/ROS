package com.raj.ros.shell;

@SuppressWarnings("unused")
public interface ShellExitCode {

  int SUCCESS = 0;

  int WATCHDOG_EXIT = -1;

  int SHELL_DIED = -2;

  int SHELL_EXEC_FAILED = -3;

  int SHELL_WRONG_UID = -4;

  int SHELL_NOT_FOUND = -5;

  int TERMINATED = 130;

  int COMMAND_NOT_EXECUTABLE = 126;

  int COMMAND_NOT_FOUND = 127;

}
