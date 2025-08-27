package com.dn.mygame; 
 
import android.app.Activity; 
import android.os.Bundle; 
 
public class MainActivity extends Activity { 
   private GameView gameView; 
 
   private void hideSystemUI() { 
      this.getWindow().getDecorView().setSystemUiVisibility(5894); 
   } 
 
   protected void onCreate(Bundle var1) { 
      super.onCreate(var1); 
      this.requestWindowFeature(1); 
      this.getWindow().setFlags(1024, 1024); 
      this.gameView = new GameView(this); 
      this.setContentView(this.gameView); 
      this.hideSystemUI(); 
   } 
 
   @Override
protected void onPause() {
    super.onPause();
    gameView.pause();
    gameView.saveState();
}

@Override
protected void onResume() {
    super.onResume();
    gameView.loadState();
    gameView.resume();
    hideSystemUI();
}
 
   public void onWindowFocusChanged(boolean var1) { 
      super.onWindowFocusChanged(var1); 
      if (var1) { 
         this.hideSystemUI(); 
      } 
 
   } 
} 
 
