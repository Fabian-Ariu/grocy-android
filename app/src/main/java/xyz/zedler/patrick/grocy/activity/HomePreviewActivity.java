package xyz.zedler.patrick.grocy.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import xyz.zedler.patrick.grocy.R;

/** Design-Vorschau der neuen Screens im Vollbild (randlos, transparente Systemleisten).
 *  Layout via Extra "layout"; Views mit Tag "preview_xxx" navigieren zum jeweiligen Layout. */
public class HomePreviewActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(Color.TRANSPARENT);
    window.setBackgroundDrawable(new ColorDrawable(0xFFF2F0EB));
    int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (Build.VERSION.SDK_INT >= 26) flags |= 0x10; // LIGHT_NAVIGATION_BAR
    window.getDecorView().setSystemUiVisibility(flags);

    String name = getIntent().getStringExtra("layout");
    int id = name != null ? getResources().getIdentifier(name, "layout", getPackageName()) : 0;
    if (id == 0) id = R.layout.preview_home;
    setContentView(id);

    final View content = findViewById(android.R.id.content);
    content.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
          insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
      return insets;
    });
    content.requestApplyInsets();
    wireNav(content);
  }

  private void wireNav(View root) {
    if (root.getTag() instanceof String && ((String) root.getTag()).startsWith("preview_")) {
      final String target = (String) root.getTag();
      root.setOnClickListener(v -> {
        Intent i = new Intent(this, HomePreviewActivity.class);
        i.putExtra("layout", target);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
      });
    }
    if (root instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) root;
      for (int k = 0; k < g.getChildCount(); k++) wireNav(g.getChildAt(k));
    }
  }
}
