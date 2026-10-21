/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler and Dominic Zedler
 * Copyright (c) 2024-2026 by Patrick Zedler
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: batteries)
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.PictureUtil;

/**
 * Kassenzettel (Vorlage 07): Foto -> Texterkennung (ML Kit, gebuendelt, ohne Google-Dienste)
 * -> Positionen mit Menge/Preis -> Produktzuordnung (gelernt oder unscharf) -> Einkauf buchen.
 */
public class ReceiptViewModel extends BaseViewModel {

  private static final String TAG = ReceiptViewModel.class.getSimpleName();
  private static final String PREFS_MAPPINGS = "receipt_mappings";
  private static final Pattern PRICE = Pattern.compile("(-?\\d+)[.,](\\d{2})\\s*(€|eur|chf|fr\\.?|[ab12]\\*?)?\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern QTY = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(x|×|stk\\.?|st\\.?|kg|g|l|ml|pkg\\.?)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern DATE = Pattern.compile("(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})");
  private static final String[] IGNORE = {"summe", "total", "gesamt", "zwischensumme", "rückgeld", "rueckgeld", "bar ", "karte", "maestro", "visa", "mastercard", "ec-", "mwst", "ust", "netto", "brutto", "betrag", "zahlung", "bon-nr", "bon nr", "kassier", "kasse", "filiale", "tel", "www.", "danke", "vielen dank", "uhrzeit", "datum", "trinkgeld", "gegeben", "punkte", "cumulus", "supercard", "rabatt", "aktion"};

  public static final class Line {
    public String raw, name, unitText;
    public double qty = 1; @Nullable public Double price;
    @Nullable public Product product; public boolean checked, deposit;
  }

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final AppDatabase appDatabase;
  private final SharedPreferences mappings;
  private final MutableLiveData<Boolean> isLoadingLive = new MutableLiveData<>(false);
  private final MutableLiveData<List<Line>> linesLive = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<String> subtitleLive = new MutableLiveData<>(null);
  private List<Product> products = new ArrayList<>();
  private String currentFilePath;

  public ReceiptViewModel(@NonNull Application application) {
    super(application);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    appDatabase = AppDatabase.getAppDatabase(getApplication());
    mappings = application.getSharedPreferences(PREFS_MAPPINGS, 0);
  }

  public void loadFromDatabase() {
    appDatabase.productDao().getProducts().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(p -> { products = new ArrayList<>(); for (Product x : p) if (x.isActive()) products.add(x); })
        .doOnError(e -> Log.e(TAG, "load: ", e)).onErrorComplete().subscribe();
  }

  public File createImageFile() throws IOException {
    File image = PictureUtil.createImageFile(getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES));
    currentFilePath = image.getAbsolutePath();
    return image;
  }

  public String getCurrentFilePath() { return currentFilePath; }

  public void recognize(String path) {
    if (path == null) { showErrorMessage(); return; }
    isLoadingLive.setValue(true);
    try {
      InputImage image = InputImage.fromFilePath(getApplication(), Uri.fromFile(new File(path)));
      TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
          .addOnSuccessListener(text -> { isLoadingLive.setValue(false); parse(text); })
          .addOnFailureListener(e -> { isLoadingLive.setValue(false); Log.e(TAG, "recognize: ", e); showMessage(getApplication().getString(R.string.receipt_ocr_failed)); });
    } catch (IOException e) {
      isLoadingLive.setValue(false); Log.e(TAG, "recognize: ", e); showErrorMessage();
    }
  }

  /** Zeilen von oben nach unten, Preis am Zeilenende, Mengenzeilen haengen an der vorigen Position. */
  private void parse(Text text) {
    List<Text.Line> raw = new ArrayList<>();
    for (Text.TextBlock b : text.getTextBlocks()) raw.addAll(b.getLines());
    raw.sort((a, b) -> { Rect ra = a.getBoundingBox(), rb = b.getBoundingBox(); return Integer.compare(ra == null ? 0 : ra.top, rb == null ? 0 : rb.top); });
    // Spalten zusammenfuehren: Name links und Preis rechts kommen als getrennte Zeilen auf gleicher Hoehe
    List<String> rows = new ArrayList<>();
    List<Rect> rowRects = new ArrayList<>();
    for (Text.Line l : raw) {
      Rect r = l.getBoundingBox();
      int idx = -1;
      if (r != null) {
        for (int i = rowRects.size() - 1; i >= 0 && i >= rowRects.size() - 3; i--) {
          Rect rr = rowRects.get(i);
          int tol = (int) (Math.max(rr.height(), r.height()) * 0.6);
          if (Math.abs(rr.centerY() - r.centerY()) <= tol) { idx = i; break; }
        }
      }
      if (idx < 0) { rows.add(l.getText()); rowRects.add(r == null ? new Rect() : new Rect(r)); continue; }
      Rect rr = rowRects.get(idx);
      rows.set(idx, r.left < rr.left ? l.getText() + " " + rows.get(idx) : rows.get(idx) + " " + l.getText());
      rr.union(r);
    }
    List<Line> out = new ArrayList<>();
    String store = null, date = null;
    for (String row : rows) {
      String t = row.trim();
      if (t.isEmpty()) continue;
      String low = t.toLowerCase(Locale.ROOT);
      if (store == null && t.length() > 2 && !t.matches(".*\\d.*")) store = t;
      if (date == null) { Matcher dm = DATE.matcher(t); if (dm.find()) date = dm.group(0); }
      Matcher qm = QTY.matcher(t);
      if (qm.find() && !out.isEmpty()) {
        // Reine Mengenzeile ("2 x 1.29", "1.1 kg x 1.99/kg") gehoert zur vorigen Position
        String rest = PRICE.matcher(t.substring(qm.end())).replaceAll("");
        String letters = rest.replaceAll("(?i)(x|×|\\*|/|chf|eur|fr|kg|stk|st|pkg|g|l|ml|\\d|[.,\\s])", "");
        if (letters.isEmpty()) {
          Line prev = out.get(out.size() - 1);
          try { prev.qty = Double.parseDouble(qm.group(1).replace(',', '.')); } catch (NumberFormatException ignored) {}
          prev.unitText = qm.group(2).toLowerCase(Locale.ROOT).replace(".", "");
          continue;
        }
      }
      boolean ignore = false;
      for (String k : IGNORE) if (low.contains(k)) { ignore = true; break; }
      if (ignore) continue;
      Matcher pm = PRICE.matcher(t);
      if (!pm.find()) continue;   // nur Zeilen mit Preis sind Positionen
      String name = t.substring(0, pm.start()).replaceAll("[\\s*#]+$", "").trim();
      if (name.length() < 2) continue;
      Line line = new Line();
      line.raw = name;
      try { line.price = Double.parseDouble(pm.group(1) + "." + pm.group(2)); } catch (NumberFormatException ignored) {}
      if (low.contains("pfand") || low.contains("depot") || low.contains("leergut")) { line.deposit = true; line.name = name; out.add(line); continue; }
      Matcher qm2 = QTY.matcher(name);
      if (qm2.find()) {
        try { line.qty = Double.parseDouble(qm2.group(1).replace(',', '.')); } catch (NumberFormatException ignored) {}
        line.unitText = qm2.group(2).toLowerCase(Locale.ROOT).replace(".", "");
        line.raw = name.substring(qm2.end()).trim();
      }
      line.product = match(line.raw);
      line.name = line.product != null ? line.product.getName() : null;
      line.checked = line.product != null;
      out.add(line);
    }
    String sub = (store != null ? store : getString(R.string.receipt_title)) + (date != null ? " \u00b7 " + date : "");
    subtitleLive.setValue(out.isEmpty() ? null : sub);
    linesLive.setValue(out);
    if (out.isEmpty()) showMessage(getString(R.string.receipt_nothing));
  }

  @Nullable private Product match(String rawName) {
    String key = key(rawName);
    int learned = mappings.getInt(key, -1);
    if (learned > 0) for (Product p : products) if (p.getId() == learned) return p;
    Product best = null; int bestScore = 0;
    for (Product p : products) {
      if (p.getName() == null) continue;
      int score = FuzzySearch.tokenSetRatio(key, key(p.getName()));
      if (score > bestScore) { bestScore = score; best = p; }
    }
    return bestScore >= 78 ? best : null;
  }

  private static String key(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß ]", " ").replaceAll("\\s+", " ").trim(); }

  public void assign(Line line, Product product) {
    line.product = product; line.name = product.getName(); line.checked = true;
    mappings.edit().putInt(key(line.raw), product.getId()).apply();   // beim naechsten Zettel wiedererkannt
    linesLive.setValue(linesLive.getValue());
  }

  public void toggle(Line line) { if (line.deposit) return; line.checked = !line.checked; linesLive.setValue(linesLive.getValue()); }

  public int checkedCount() {
    int n = 0; List<Line> l = linesLive.getValue();
    if (l != null) for (Line x : l) if (x.checked && x.product != null) n++;
    return n;
  }

  public List<Product> getProducts() { return products; }

  public void purchaseAll() {
    List<Line> l = linesLive.getValue();
    if (l == null) return;
    List<Line> todo = new ArrayList<>();
    for (Line x : l) if (x.checked && x.product != null) todo.add(x);
    if (todo.isEmpty()) { showMessage(getString(R.string.receipt_none_checked)); return; }
    purchaseNext(todo, 0, 0);
  }

  private void purchaseNext(List<Line> todo, int i, int done) {
    if (i >= todo.size()) {
      showMessage(getApplication().getResources().getQuantityString(R.plurals.receipt_done, done, done));
      linesLive.setValue(new ArrayList<>()); subtitleLive.setValue(null);
      return;
    }
    Line line = todo.get(i); Product p = line.product;
    JSONObject body = new JSONObject();
    try {
      body.put("amount", line.qty);
      int days = p.getDefaultDueDaysInt();
      body.put("best_before_date", days > 0 ? DateUtil.getTodayWithDaysAdded(days) : Constants.DATE.NEVER_OVERDUE);
      body.put("transaction_type", "purchase");
      if (line.price != null && line.qty > 0) body.put("price", String.format(Locale.US, "%.2f", line.price / line.qty));
      if (p.getLocationIdInt() > 0) body.put("location_id", String.valueOf(p.getLocationIdInt()));
    } catch (Exception e) { Log.e(TAG, "purchase: ", e); }
    dlHelper.postWithArray(grocyApi.purchaseProduct(p.getId()), body,
        r -> { mappings.edit().putInt(key(line.raw), p.getId()).apply(); purchaseNext(todo, i + 1, done + 1); },
        error -> { Log.e(TAG, "purchase: " + error); showNetworkErrorMessage(error); purchaseNext(todo, i + 1, done); });
  }

  public MutableLiveData<List<Line>> getLinesLive() { return linesLive; }
  public MutableLiveData<String> getSubtitleLive() { return subtitleLive; }
  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }
  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
