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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.ProduceCatalog;

/**
 * Schnellerfassung Obst & Gemuese (Vorlage 04): eingebauter Katalog + Grocy-Produkte,
 * "Haeufig" nach Gewohnheit (Nutzungszaehler), fehlende Produkte werden beim Einlagern angelegt.
 */
public class QuickAddViewModel extends BaseViewModel {

  private static final String TAG = QuickAddViewModel.class.getSimpleName();
  private static final String PREFS_USAGE = "quick_add_usage";
  public static final String GROUP_NAME = "Obst & Gemüse";
  public static final int CAT_FREQUENT = -1;
  public static final int CAT_OTHER = ProduceCatalog.CATEGORIES.length;

  public static final class Item {
    public final String key, name; public final int category; public int days; public Product product;
    Item(String name, int category, int days, Product product) {
      this.key = ProduceCatalog.key(name); this.name = name; this.category = category; this.days = days; this.product = product;
    }
  }

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final AppDatabase appDatabase;
  private final SharedPreferences usage;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<List<Item>> itemsLive = new MutableLiveData<>(new ArrayList<>());   // gefiltert
  private final MutableLiveData<Map<String, Integer>> countsLive = new MutableLiveData<>(new HashMap<>());
  private final MutableLiveData<Boolean> hasOtherLive = new MutableLiveData<>(false);
  private final LinkedHashMap<String, Item> allItems = new LinkedHashMap<>();
  private List<ProductGroup> groups = new ArrayList<>();
  private List<QuantityUnit> units = new ArrayList<>();
  private List<Location> locations = new ArrayList<>();
  private int selectedCategory = CAT_FREQUENT;
  private String search = "";

  public QuickAddViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    appDatabase = AppDatabase.getAppDatabase(getApplication());
    usage = application.getSharedPreferences(PREFS_USAGE, 0);
  }

  public void loadFromDatabase() {
    Single.zip(appDatabase.productDao().getProducts(), appDatabase.productGroupDao().getProductGroups(),
        appDatabase.quantityUnitDao().getQuantityUnits(), appDatabase.locationDao().getLocations(),
        (p, g, q, l) -> new Object[]{p, g, q, l})
        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(d -> { groups = cast(d[1]); units = cast(d[2]); locations = cast(d[3]); buildItems(cast(d[0])); applyFilter(); })
        .doOnError(e -> Log.e(TAG, "loadFromDatabase: ", e)).onErrorComplete().subscribe();
  }

  @SuppressWarnings("unchecked") private static <T> List<T> cast(Object o) { return (List<T>) o; }

  /** Katalog zuerst, dann Grocy-Produkte: gleicher Name -> verknuepfen, sonst als "Weitere" (nur Obst-&-Gemuese-Gruppe). */
  private void buildItems(List<Product> products) {
    allItems.clear();
    for (ProduceCatalog.Entry e : ProduceCatalog.ENTRIES) allItems.put(ProduceCatalog.key(e.name), new Item(e.name, e.category, e.days, null));
    Integer gid = groupId();
    boolean other = false;
    for (Product p : products) {
      if (p.getName() == null || !p.isActive()) continue;
      String key = ProduceCatalog.key(p.getName());
      Item item = allItems.get(key);
      if (item != null) {
        item.product = p;
        if (p.getDefaultDueDaysInt() > 0) item.days = p.getDefaultDueDaysInt();
      } else if (gid != null && String.valueOf(gid).equals(p.getProductGroupId())) {
        allItems.put(key, new Item(p.getName(), CAT_OTHER, p.getDefaultDueDaysInt(), p));
        other = true;
      }
    }
    hasOtherLive.setValue(other);
  }

  private Integer groupId() {
    for (ProductGroup g : groups) {
      String n = g.getName() == null ? "" : g.getName().toLowerCase();
      if (n.equals(GROUP_NAME.toLowerCase()) || n.contains("obst") || n.contains("gemüse") || n.contains("gemuese")) return g.getId();
    }
    return null;
  }

  public void selectCategory(int category) { selectedCategory = category; applyFilter(); }
  public int getSelectedCategory() { return selectedCategory; }
  public void setSearch(String s) { search = s == null ? "" : s.trim().toLowerCase(); applyFilter(); }

  private int usageOf(Item i) { return usage.getInt(i.key, 0); }

  private void applyFilter() {
    List<Item> out = new ArrayList<>();
    if (!search.isEmpty()) {
      for (Item i : allItems.values()) if (i.name.toLowerCase().contains(search)) out.add(i);
    } else if (selectedCategory == CAT_FREQUENT) {
      // Gewohnheit: meistgenutzte zuerst, aufgefuellt mit der Standardauswahl
      for (Item i : allItems.values()) if (usageOf(i) > 0) out.add(i);
      out.sort((a, b) -> Integer.compare(usageOf(b), usageOf(a)));
      for (String n : ProduceCatalog.DEFAULT_FREQUENT) {
        Item i = allItems.get(ProduceCatalog.key(n));
        if (i != null && !out.contains(i) && out.size() < 9) out.add(i);
      }
    } else {
      for (Item i : allItems.values()) if (i.category == selectedCategory) out.add(i);
      out.sort((a, b) -> Integer.compare(usageOf(b), usageOf(a)));   // stabil: Katalogreihenfolge bleibt bei Gleichstand
    }
    itemsLive.setValue(out);
  }

  public void increment(Item i) { change(i, 1); }
  public void decrement(Item i) { change(i, -1); }

  private void change(Item i, int delta) {
    Map<String, Integer> m = new HashMap<>(countsLive.getValue() == null ? new HashMap<>() : countsLive.getValue());
    int c = (m.containsKey(i.key) ? m.get(i.key) : 0) + delta;
    if (c <= 0) m.remove(i.key); else m.put(i.key, c);
    countsLive.setValue(m);
  }

  public int totalCount() {
    int t = 0; Map<String, Integer> m = countsLive.getValue();
    if (m != null) for (int c : m.values()) t += c;
    return t;
  }

  public Item itemByKey(String key) { return allItems.get(key); }

  /** Alle gezaehlten Sorten nacheinander einlagern; fehlende Produkte werden vorher in Grocy angelegt. */
  public void purchaseAll() {
    Map<String, Integer> m = countsLive.getValue();
    if (m == null || m.isEmpty()) return;
    List<String> keys = new ArrayList<>(m.keySet());
    ensureGroup(gid -> purchaseNext(keys, 0, m, 0, gid));
  }

  private void purchaseNext(List<String> keys, int index, Map<String, Integer> counts, int done, Integer gid) {
    if (index >= keys.size()) {
      countsLive.setValue(new HashMap<>());
      applyFilter();
      showMessage(getApplication().getString(R.string.quick_add_done, done));
      return;
    }
    Item item = allItems.get(keys.get(index));
    Integer amount = counts.get(keys.get(index));
    if (item == null || amount == null) { purchaseNext(keys, index + 1, counts, done, gid); return; }
    ensureProduct(item, gid, () -> {
      JSONObject body = new JSONObject();
      try {
        body.put("amount", amount);
        body.put("best_before_date", item.days > 0 ? DateUtil.getTodayWithDaysAdded(item.days) : Constants.DATE.NEVER_OVERDUE);
        body.put("transaction_type", "purchase");
        if (item.product.getLocationIdInt() > 0) body.put("location_id", String.valueOf(item.product.getLocationIdInt()));
      } catch (Exception e) { Log.e(TAG, "purchaseNext: ", e); }
      dlHelper.postWithArray(grocyApi.purchaseProduct(item.product.getId()), body,
          response -> { usage.edit().putInt(item.key, usageOf(item) + amount).apply(); purchaseNext(keys, index + 1, counts, done + amount, gid); },
          error -> { Log.e(TAG, "purchaseNext: " + error); showNetworkErrorMessage(error); purchaseNext(keys, index + 1, counts, done, gid); });
    });
  }

  private void ensureGroup(java.util.function.Consumer<Integer> next) {
    Integer gid = groupId();
    if (gid != null) { next.accept(gid); return; }
    JSONObject body = new JSONObject();
    try { body.put("name", GROUP_NAME); body.put("description", ""); } catch (Exception e) { Log.e(TAG, "group: ", e); }
    dlHelper.post(grocyApi.getObjects(ENTITY.PRODUCT_GROUPS), body,
        r -> next.accept(r.optInt("created_object_id", 0) > 0 ? r.optInt("created_object_id") : null),
        error -> { Log.e(TAG, "group: " + error); showNetworkErrorMessage(error); next.accept(null); });
  }

  private int unitId() {
    for (QuantityUnit q : units) {
      String n = q.getName() == null ? "" : q.getName().toLowerCase();
      if (n.startsWith("stück") || n.startsWith("stueck") || n.equals("stk") || n.startsWith("piece")) return q.getId();
    }
    return units.isEmpty() ? 1 : units.get(0).getId();
  }

  private void ensureProduct(Item item, Integer gid, Runnable next) {
    if (item.product != null) { next.run(); return; }
    JSONObject body = new JSONObject();
    try {
      body.put("name", item.name); body.put("description", "");
      if (gid != null) body.put("product_group_id", String.valueOf(gid));
      if (!locations.isEmpty()) body.put("location_id", String.valueOf(locations.get(0).getId()));
      int qu = unitId();
      body.put("qu_id_purchase", String.valueOf(qu)); body.put("qu_id_stock", String.valueOf(qu));
      body.put("qu_factor_purchase_to_stock", "1"); body.put("min_stock_amount", "0");
      body.put("default_best_before_days", String.valueOf(item.days)); body.put("active", "1");
    } catch (Exception e) { Log.e(TAG, "product: ", e); }
    dlHelper.post(grocyApi.getObjects(ENTITY.PRODUCTS), body, r -> {
      int id = r.optInt("created_object_id", 0);
      if (id <= 0) { showErrorMessage(); return; }
      dlHelper.get(grocyApi.getObject(ENTITY.PRODUCTS, id), TAG, resp -> {
        try { Product p = new com.google.gson.Gson().fromJson(resp, Product.class); if (p != null) { item.product = p; next.run(); return; } }
        catch (Exception e) { Log.e(TAG, "product parse: ", e); }
        showErrorMessage();
      }, error -> { Log.e(TAG, "product get: " + error); showNetworkErrorMessage(error); });
    }, error -> { Log.e(TAG, "product create: " + error); showNetworkErrorMessage(error); });
  }

  public MutableLiveData<List<Item>> getItemsLive() { return itemsLive; }
  public MutableLiveData<Map<String, Integer>> getCountsLive() { return countsLive; }
  public MutableLiveData<Boolean> getHasOtherLive() { return hasOtherLive; }
  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }

  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
