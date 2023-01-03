package com.raj.ros.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.raj.ros.terminal.TerminalSession;


public interface TerminalViewClient {

    float onScale(float scale);

    void onSingleTapUp(MotionEvent e);

    boolean shouldBackButtonBeMappedToEscape();

    void copyModeChanged(boolean copyMode);

    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean readControlKey();

    boolean readAltKey();

    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

    boolean onLongPress(MotionEvent event);

}
