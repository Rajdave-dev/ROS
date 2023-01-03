package com.raj.ros;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import com.raj.ros.helper.Helper;

public class CrashHandler extends AppCompatActivity {
    
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Helper.hideSystemUI(getWindow());
	setContentView(R.layout.activity_crash);
    com.google.android.material.textview.MaterialTextView tv = findViewById(R.id.crashDetails);
    tv.setTextIsSelectable(true);
    tv.setText(CustomActivityOnCrash.getAllErrorDetailsFromIntent(getApplicationContext(),getIntent()));
  }
}
