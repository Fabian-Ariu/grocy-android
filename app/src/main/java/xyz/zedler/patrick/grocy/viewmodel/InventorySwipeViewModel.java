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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: swipe inventory)
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.StockItem;
import xyz.zedler.patrick.grocy.util.NumUtil;

/** Schnelle Inventur (Vorlage 08, Swipe-Stapel): rechts = stimmt, links = weg, Stepper = korrigiert.
 *  Jede Entscheidung bucht POST /stock/products/{id}/inventory mit der neuen Menge. */
public class InventorySwipeViewModel extends BaseViewModel {

  private static final String TAG = InventorySwipeViewModel.class.getSimpleName();

  public static class Step { final StockItem item; final double oldAmount; final double newAmount;
    Step(StockItem i, double o, double n) { item = i; oldAmount = o; newAmount = n; } }

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final AppDatabase appDatabase;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<StockItem> currentLive;
  private final MutableLiveData<String> progressLive;
  private final Deque<StockItem> queue = new ArrayDeque<>();
  private final Deque<Step> history = new ArrayDeque<>();
  private final Map<Integer, QuantityUnit> units = new HashMap<>();
  private final Map<Integer, Product> products = new HashMap<>();
  private int total = 0, done = 0, corrected = 0;

  public InventorySwipeViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    appDatabase = AppDatabase.getAppDatabase(getApplication());
    currentLive = new MutableLiveData<>();
    progressLive = new MutableLiveData<>("");
  }

  public void loadFromDatabase() {
    Single.zip(appDatabase.stockItemDao().getStockItems(), appDatabase.quantityUnitDao().getQuantityUnits(),
        appDatabase.productDao().getProducts(), (items, qus, prods) -> new Object[]{items, qus, prods})
        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(data -> {
          @SuppressWarnings("unchecked") List<StockItem> items = (List<StockItem>) data[0];
          @SuppressWarnings("unchecked") List<QuantityUnit> qus = (List<QuantityUnit>) data[1];
          @SuppressWarnings("unchecked") List<Product> prods = (List<Product>) data[2];
          units.clear(); for (QuantityUnit q : qus) units.put(q.getId(), q);
          products.clear(); for (Product pr : prods) products.put(pr.getId(), pr);
          queue.clear(); history.clear(); done = 0; corrected = 0;
          for (StockItem i : items) if (products.containsKey(i.getProductId()) && i.getAmountAggregatedDouble() > 0) queue.add(i);
          total = queue.size();
          next();
        })
        .doOnError(e -> Log.e(TAG, "load: ", e)).onErrorComplete().subscribe();
  }

  private void next() {
    currentLive.setValue(queue.peek());
    progressLive.setValue(getApplication().getString(R.string.inv_progress, done, total, corrected));
  }

  public String amountText(StockItem item) {
    if (item == null) return "";
    double a = item.getAmountAggregatedDouble();
    Product pr = products.get(item.getProductId());
    QuantityUnit q = pr == null ? null : units.get(pr.getQuIdStockInt());
    String unit = q == null ? "" : (a == 1 ? q.getName() : q.getNamePlural());
    return NumUtil.trimAmount(a, 2) + " " + unit;
  }

  public String productName(StockItem item) {
    Product pr = item == null ? null : products.get(item.getProductId());
    return pr == null ? "" : pr.getName();
  }

  public double currentAmount() { StockItem i = queue.peek(); return i == null ? 0 : i.getAmountAggregatedDouble(); }
  public boolean isFinished() { return total > 0 && queue.isEmpty(); }
  public boolean canUndo() { return !history.isEmpty(); }

  /** Entscheidung buchen: newAmount == alt -> nur Inventurdatum setzen. */
  public void decide(double newAmount) {
    StockItem item = queue.poll();
    if (item == null) return;
    double old = item.getAmountAggregatedDouble();
    boolean changed = Math.abs(newAmount - old) > 0.0001;
    done++; if (changed) corrected++;
    history.push(new Step(item, old, newAmount));
    post(item, newAmount, null);
    next();
  }

  public void undo() {
    Step s = history.poll();
    if (s == null) return;
    done--; if (Math.abs(s.newAmount - s.oldAmount) > 0.0001) { corrected--; post(s.item, s.oldAmount, null); }
    queue.addFirst(s.item);
    next();
  }

  private void post(StockItem item, double newAmount, Runnable onDone) {
    JSONObject body = new JSONObject();
    try { body.put("new_amount", newAmount); } catch (Exception e) { Log.e(TAG, "post: ", e); }
    dlHelper.postWithArray(grocyApi.inventoryProduct(item.getProductId()), body,
        r -> { if (onDone != null) onDone.run(); },
        error -> { Log.e(TAG, "inventory: " + error); showNetworkErrorMessage(error); });
  }

  public MutableLiveData<StockItem> getCurrentLive() { return currentLive; }
  public MutableLiveData<String> getProgressLive() { return progressLive; }
  public int getTotal() { return total; } public int getDone() { return done; }
  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
