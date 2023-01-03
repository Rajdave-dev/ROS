package com.raj.ros.shell;

import java.io.IOException;


public class ShellNotFoundException extends IOException {

  public ShellNotFoundException(String detailMessage) {
    super(detailMessage);
  }

  public ShellNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

}
