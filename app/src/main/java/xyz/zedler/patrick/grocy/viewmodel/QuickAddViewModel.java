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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.util.DateUtil;

/** Schnellerfassung (Vorlage 04): Antippen = +1, Haltbarkeit aus den Produkt-Standardtagen,
 *  "einlagern" bucht jedes Produkt ueber POST /stock/products/{id}/add. */
public class QuickAddViewModel extends BaseViewModel {

  private static final String TAG = QuickAddViewModel.class.getSimpleName();

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final AppDatabase appDatabase;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<List<Product>> productsLive;      // gefiltert (Gruppe + Suche)
  private final MutableLiveData<List<ProductGroup>> groupsLive;
  private final MutableLiveData<Map<Integer, Integer>> countsLive;
  private List<Product> allProducts = new ArrayList<>();
  private int selectedGroupId = -1;
  private String search = "";

  public QuickAddViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    appDatabase = AppDatabase.getAppDatabase(getApplication());
    productsLive = new MutableLiveData<>(new ArrayList<>());
    groupsLive = new MutableLiveData<>(new ArrayList<>());
    countsLive = new MutableLiveData<>(new HashMap<>());
  }

  public void loadFromDatabase() {
    Single.zip(
        appDatabase.productDao().getProducts(),
        appDatabase.productGroupDao().getProductGroups(),
        (products, groups) -> new Object[]{products, groups}
    )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(data -> {
          @SuppressWarnings("unchecked") List<Product> products = (List<Product>) data[0];
          @SuppressWarnings("unchecked") List<ProductGroup> groups = (List<ProductGroup>) data[1];
          allProducts = products;
          groupsLive.setValue(groups);
          if (selectedGroupId == -1) {
            // Vorlage: Obst & Gemuese als Startgruppe, falls vorhanden
            for (ProductGroup g : groups) {
              String n = g.getName() == null ? "" : g.getName().toLowerCase();
              if (n.contains("obst") || n.contains("gem") || n.contains("fruit") || n.contains("veg")) {
                selectedGroupId = g.getId();
                break;
              }
            }
          }
          applyFilter();
        })
        .doOnError(e -> Log.e(TAG, "loadFromDatabase: ", e))
        .onErrorComplete()
        .subscribe();
  }

  public void selectGroup(int groupId) {
    selectedGroupId = groupId;
    applyFilter();
  }

  public int getSelectedGroupId() {
    return selectedGroupId;
  }

  public void setSearch(String s) {
    search = s == null ? "" : s.trim().toLowerCase();
    applyFilter();
  }

  private void applyFilter() {
    List<Product> out = new ArrayList<>();
    for (Product p : allProducts) {
      if (selectedGroupId > 0) {
        String gid = p.getProductGroupId();
        if (gid == null || !gid.equals(String.valueOf(selectedGroupId))) continue;
      }
      if (!search.isEmpty() && (p.getName() == null || !p.getName().toLowerCase().contains(search))) continue;
      out.add(p);
    }
    productsLive.setValue(out);
  }

  public void increment(Product p) {
    Map<Integer, Integer> m = new HashMap<>(countsLive.getValue() == null ? new HashMap<>() : countsLive.getValue());
    Integer c = m.get(p.getId());
    m.put(p.getId(), c == null ? 1 : c + 1);
    countsLive.setValue(m);
  }

  public void decrement(Product p) {
    Map<Integer, Integer> m = new HashMap<>(countsLive.getValue() == null ? new HashMap<>() : countsLive.getValue());
    Integer c = m.get(p.getId());
    if (c == null) return;
    if (c <= 1) m.remove(p.getId()); else m.put(p.getId(), c - 1);
    countsLive.setValue(m);
  }

  public int totalCount() {
    int t = 0;
    Map<Integer, Integer> m = countsLive.getValue();
    if (m != null) for (int c : m.values()) t += c;
    return t;
  }

  public Product productById(int id) {
    for (Product p : allProducts) if (p.getId() == id) return p;
    return null;
  }

  /** Alle gezaehlten Produkte nacheinander einlagern (echte Einkaufsbuchungen). */
  public void purchaseAll() {
    Map<Integer, Integer> m = countsLive.getValue();
    if (m == null || m.isEmpty()) return;
    List<Integer> ids = new ArrayList<>(m.keySet());
    purchaseNext(ids, 0, m, 0);
  }

  private void purchaseNext(List<Integer> ids, int index, Map<Integer, Integer> counts, int done) {
    if (index >= ids.size()) {
      countsLive.setValue(new HashMap<>());
      showMessage(getApplication().getString(R.string.quick_add_done, done));
      return;
    }
    int id = ids.get(index);
    Product p = productById(id);
    Integer amount = counts.get(id);
    if (p == null || amount == null) {
      purchaseNext(ids, index + 1, counts, done);
      return;
    }
    JSONObject body = new JSONObject();
    try {
      body.put("amount", amount);
      int days = p.getDefaultDueDaysInt();
      body.put("best_before_date", days > 0 ? DateUtil.getTodayWithDaysAdded(days) : Constants.DATE.NEVER_OVERDUE);
      body.put("transaction_type", "purchase");
      if (p.getLocationIdInt() > 0) body.put("location_id", String.valueOf(p.getLocationIdInt()));
    } catch (Exception e) {
      Log.e(TAG, "purchaseNext: ", e);
    }
    dlHelper.postWithArray(grocyApi.purchaseProduct(id), body,
        response -> purchaseNext(ids, index + 1, counts, done + amount),
        error -> {
          Log.e(TAG, "purchaseNext: " + error);
          showNetworkErrorMessage(error);
          purchaseNext(ids, index + 1, counts, done);
        }
    );
  }

  public MutableLiveData<List<Product>> getProductsLive() { return productsLive; }
  public MutableLiveData<List<ProductGroup>> getGroupsLive() { return groupsLive; }
  public MutableLiveData<Map<Integer, Integer>> getCountsLive() { return countsLive; }
  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }

  @Override
  protected void onCleared() {
    dlHelper.destroy();
    super.onCleared();
  }
}
