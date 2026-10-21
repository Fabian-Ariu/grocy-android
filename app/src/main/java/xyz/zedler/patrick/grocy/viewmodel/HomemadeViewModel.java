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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: homemade)
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.Recipe;
import xyz.zedler.patrick.grocy.util.DateUtil;

/** Selbstgemacht (Vorlage 05/06): Glaeser einlagern, Produkt bei Bedarf anlegen, Rezept
 *  verknuepfen (Rezept "erzeugt Produkt"), optional Etiketten am Server-Drucker. */
public class HomemadeViewModel extends BaseViewModel {

  private static final String TAG = HomemadeViewModel.class.getSimpleName();
  public static final String GROUP_NAME = "Selbstgemacht";
  public static final String[] CATEGORIES = {"Eingekocht", "Eingefroren", "Eingelegt", "Getrocknet"};
  public static final int[] CATEGORY_DAYS = {365, 180, 180, 365};

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final AppDatabase appDatabase;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<List<Product>> homemadeProductsLive = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<Recipe>> recipesLive = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<Location>> locationsLive = new MutableLiveData<>(new ArrayList<>());
  private List<Product> allProducts = new ArrayList<>();
  private List<QuantityUnit> units = new ArrayList<>();
  private List<ProductGroup> groups = new ArrayList<>();

  public HomemadeViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    appDatabase = AppDatabase.getAppDatabase(getApplication());
  }

  public void loadFromDatabase() {
    Single.zip(appDatabase.productDao().getProducts(), appDatabase.productGroupDao().getProductGroups(),
        appDatabase.quantityUnitDao().getQuantityUnits(), appDatabase.locationDao().getLocations(),
        appDatabase.recipeDao().getRecipes(), (p, g, q, l, r) -> new Object[]{p, g, q, l, r})
        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(d -> {
          allProducts = cast(d[0]); groups = cast(d[1]); units = cast(d[2]);
          locationsLive.setValue(cast(d[3])); recipesLive.setValue(cast(d[4]));
          Integer gid = groupId();
          List<Product> hm = new ArrayList<>();
          for (Product p : allProducts) if (gid != null && String.valueOf(gid).equals(p.getProductGroupId())) hm.add(p);
          homemadeProductsLive.setValue(hm);
        })
        .doOnError(e -> Log.e(TAG, "load: ", e)).onErrorComplete().subscribe();
  }

  @SuppressWarnings("unchecked") private static <T> List<T> cast(Object o) { return (List<T>) o; }

  private Integer groupId() {
    for (ProductGroup g : groups) if (g.getName() != null && g.getName().equalsIgnoreCase(GROUP_NAME)) return g.getId();
    return null;
  }
  private Product findProduct(String name) {
    for (Product p : allProducts) if (p.getName() != null && p.getName().equalsIgnoreCase(name.trim())) return p;
    return null;
  }
  private int unitId() {
    for (QuantityUnit q : units) if (q.getName() != null && (q.getName().equalsIgnoreCase("Glas") || q.getName().equalsIgnoreCase("Gläser"))) return q.getId();
    return units.isEmpty() ? 1 : units.get(0).getId();
  }

  /** Ablauf: Gruppe sicherstellen -> Produkt sicherstellen -> Rezept verknuepfen -> einlagern -> Etiketten. */
  public void save(String productName, int categoryIndex, int jars, Location location, Integer recipeId, boolean printLabels) {
    if (productName == null || productName.trim().isEmpty() || jars <= 0) { showMessage(getApplication().getString(R.string.hm_missing)); return; }
    int days = CATEGORY_DAYS[Math.max(0, Math.min(categoryIndex, CATEGORY_DAYS.length - 1))];
    ensureGroup(gid -> ensureProduct(productName.trim(), gid, location, days, product -> {
      Runnable afterLink = () -> purchase(product, jars, days, location, () -> {
        if (printLabels) printLabels(product, jars, () -> finish(jars, true));
        else finish(jars, false);
      });
      if (recipeId != null) linkRecipe(recipeId, product, afterLink); else afterLink.run();
    }));
  }

  private void finish(int jars, boolean printed) {
    showMessage(getApplication().getResources().getQuantityString(printed ? R.plurals.hm_done_labels : R.plurals.hm_done, jars, jars));
    sendEvent(Event.NAVIGATE_UP);
  }

  private void ensureGroup(java.util.function.Consumer<Integer> next) {
    Integer gid = groupId();
    if (gid != null) { next.accept(gid); return; }
    JSONObject body = new JSONObject();
    try { body.put("name", GROUP_NAME); body.put("description", ""); } catch (Exception e) { Log.e(TAG, "group: ", e); }
    dlHelper.post(grocyApi.getObjects(ENTITY.PRODUCT_GROUPS), body,
        r -> next.accept(r.optInt("created_object_id", 0) > 0 ? r.optInt("created_object_id") : null),
        error -> { Log.e(TAG, "group: " + error); showNetworkErrorMessage(error); });
  }

  private void ensureProduct(String name, Integer gid, Location location, int days, java.util.function.Consumer<Product> next) {
    Product existing = findProduct(name);
    if (existing != null) { next.accept(existing); return; }
    JSONObject body = new JSONObject();
    try {
      body.put("name", name);
      body.put("description", "");
      if (gid != null) body.put("product_group_id", String.valueOf(gid));
      if (location != null) body.put("location_id", String.valueOf(location.getId()));
      int qu = unitId();
      body.put("qu_id_purchase", String.valueOf(qu)); body.put("qu_id_stock", String.valueOf(qu));
      body.put("qu_factor_purchase_to_stock", "1"); body.put("min_stock_amount", "0");
      body.put("default_best_before_days", String.valueOf(days)); body.put("active", "1");
    } catch (Exception e) { Log.e(TAG, "product: ", e); }
    dlHelper.post(grocyApi.getObjects(ENTITY.PRODUCTS), body, r -> {
      int id = r.optInt("created_object_id", 0);
      if (id <= 0) { showErrorMessage(); return; }
      // minimales Produktobjekt fuer den weiteren Ablauf
      dlHelper.get(grocyApi.getObject(ENTITY.PRODUCTS, id), TAG, resp -> {
        try { Product p = new com.google.gson.Gson().fromJson(resp, Product.class); if (p != null) { allProducts.add(p); next.accept(p); return; } } catch (Exception e) { Log.e(TAG, "product parse: ", e); }
        showErrorMessage();
      }, error -> { Log.e(TAG, "product get: " + error); showNetworkErrorMessage(error); });
    }, error -> { Log.e(TAG, "product create: " + error); showNetworkErrorMessage(error); });
  }

  private void linkRecipe(int recipeId, Product product, Runnable next) {
    JSONObject body = new JSONObject();
    try { body.put("product_id", String.valueOf(product.getId())); } catch (Exception e) { Log.e(TAG, "recipe: ", e); }
    dlHelper.put(grocyApi.getObject(ENTITY.RECIPES, recipeId), body, r -> next.run(),
        error -> { Log.e(TAG, "recipe link: " + error); showNetworkErrorMessage(error); next.run(); });
  }

  private void purchase(Product product, int jars, int days, Location location, Runnable next) {
    JSONObject body = new JSONObject();
    try {
      body.put("amount", jars);
      body.put("best_before_date", DateUtil.getTodayWithDaysAdded(days));
      body.put("transaction_type", "purchase");
      if (location != null) body.put("location_id", String.valueOf(location.getId()));
    } catch (Exception e) { Log.e(TAG, "purchase: ", e); }
    dlHelper.postWithArray(grocyApi.purchaseProduct(product.getId()), body, r -> next.run(),
        error -> { Log.e(TAG, "purchase: " + error); showNetworkErrorMessage(error); });
  }

  /** Neueste Bestandseintraege des Produkts drucken (ein Etikett pro Glas). */
  private void printLabels(Product product, int jars, Runnable next) {
    dlHelper.get(grocyApi.getStockEntriesFromProduct(product.getId()), TAG, resp -> {
      List<Integer> ids = new ArrayList<>();
      try {
        JSONArray a = new JSONArray(resp);
        for (int i = 0; i < a.length(); i++) ids.add(a.getJSONObject(i).optInt("id"));
      } catch (Exception e) { Log.e(TAG, "entries: ", e); }
      ids.sort((x, y) -> Integer.compare(y, x));
      List<Integer> newest = ids.subList(0, Math.min(jars, ids.size()));
      printNext(newest, 0, next);
    }, error -> { Log.e(TAG, "entries: " + error); showNetworkErrorMessage(error); next.run(); });
  }

  private void printNext(List<Integer> ids, int i, Runnable next) {
    if (i >= ids.size()) { next.run(); return; }
    dlHelper.get(grocyApi.printStockEntryLabel(ids.get(i)), TAG, r -> printNext(ids, i + 1, next),
        error -> { Log.e(TAG, "print: " + error); showMessage(getApplication().getString(R.string.hm_print_failed)); next.run(); });
  }

  public MutableLiveData<List<Product>> getHomemadeProductsLive() { return homemadeProductsLive; }
  public MutableLiveData<List<Recipe>> getRecipesLive() { return recipesLive; }
  public MutableLiveData<List<Location>> getLocationsLive() { return locationsLive; }
  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }
  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
