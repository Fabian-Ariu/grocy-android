package xyz.zedler.patrick.grocy.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import xyz.zedler.patrick.grocy.R;

/** Isolierte Design-Vorschau der neuen Screens. Layout via Extra "layout" (z. B. preview_vorrat). */
public class HomePreviewActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String name = getIntent().getStringExtra("layout");
    int id = 0;
    if (name != null) id = getResources().getIdentifier(name, "layout", getPackageName());
    if (id == 0) id = R.layout.preview_home;
    setContentView(id);
  }
}
