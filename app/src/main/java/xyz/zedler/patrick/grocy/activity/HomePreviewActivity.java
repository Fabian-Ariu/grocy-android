package xyz.zedler.patrick.grocy.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import xyz.zedler.patrick.grocy.R;

/** Design-Vorschau der neuen Screens. Layout via Extra "layout".
 *  Views mit String-Tag "preview_xxx" wirken als Navigation zum jeweiligen Layout. */
public class HomePreviewActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String name = getIntent().getStringExtra("layout");
    int id = name != null ? getResources().getIdentifier(name, "layout", getPackageName()) : 0;
    if (id == 0) id = R.layout.preview_home;
    setContentView(id);
    wireNav(findViewById(android.R.id.content));
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
