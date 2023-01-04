package com.raj.ros;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.raj.ros.helper.Helper;
import com.raj.ros.terminal.TerminalActivity;
import java.io.File;
import com.raj.ros.R;


public class MainActivity extends AppCompatActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helper.hideSystemUI(getWindow());
		setContentView(R.layout.activity_main);
        if(!(new File(Variables.SYSTEM_EXEC_DIR+"/sh")).exists()){
            startActivity(new Intent(MainActivity.this,InstallerActivity.class));
            finish();
        }else{
        startActivity(new Intent(MainActivity.this,TerminalActivity.class));
        finish();
        }
    }
}