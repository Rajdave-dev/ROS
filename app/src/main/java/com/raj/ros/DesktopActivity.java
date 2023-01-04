package com.raj.ros;

import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.view.Display;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.raj.ros.Variables;
import com.raj.ros.helper.Helper;
import java.io.File;

public class DesktopActivity extends AppCompatActivity {

    private ImageButton startMenu;
    private LinearLayout deskItems, favourite;
    private ImageView wallpaper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Helper.hideSystemUI(getWindow());
        setContentView(R.layout.activity_desktop);
        findLayouts();
        setWallpaper();
        setUI();
    }
    
    private void setUI(){
    Glide.with(this).load(new File(Variables.ICONS_FOR_APP_DIR+"/menu.png")).into(startMenu);
    addDeskItems();
    }
    
    private void findLayouts(){
    startMenu = findViewById(R.id.desktop_startMenu);
    deskItems = findViewById(R.id.desktop_deskItems);
    favourite = findViewById(R.id.desktop_favourite);
    }
    
    private void addDeskItems(){
    ImageButton terminal = createDeskAppLauncher(Variables.ICONS_FOR_APP_DIR+"/terminal.png");
    deskItems.addView(terminal);
    }
    
    private ImageButton createDeskAppLauncher(String Icon){
    ImageButton item = new ImageButton(this);
   // item.setMinimumHeight(50);
  //  item.setMinimumWidth(50);
    item.setClickable(true);
    item.setLayoutParams(new LinearLayout.LayoutParams(50*5,50*5));
    Glide.with(this).load(new File(Icon)).into(item);
    return item;
    }
    
    private void setWallpaper(){
    wallpaper = findViewById(R.id.desktop_wallpaper);
    Glide.with(this).load(new File(Variables.WALLPAPER_DIR+"/ros_wallpaper.gif")).into(wallpaper);
    }
}
