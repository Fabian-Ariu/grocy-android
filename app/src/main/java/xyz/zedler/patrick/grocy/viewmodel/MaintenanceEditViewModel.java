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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: equipment & maintenance)
 */

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Event;

/** Wartung anlegen (Vorlage 22): legt eine Grocy-Chore mit Intervall an, verknuepft ueber #geraet:<id>. */
public class MaintenanceEditViewModel extends BaseViewModel {

  private static final String TAG = MaintenanceEditViewModel.class.getSimpleName();
  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final MutableLiveData<Boolean> isLoadingLive;

  public MaintenanceEditViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
  }

  /** @param afterLast true = "ab letzter Wartung" (dynamic-regular), false = fester Rhythmus (daily, alle N Tage) */
  public void save(int equipmentId, String equipmentName, String name, int days, boolean afterLast, String note) {
    JSONObject body = new JSONObject();
    try {
      body.put("name", name + " (" + equipmentName + ")");
      body.put("description", (note == null || note.isEmpty() ? "" : note + " ") + EquipmentViewModel.TAG_PREFIX + equipmentId);
      body.put("period_type", afterLast ? "dynamic-regular" : "daily");
      body.put("period_days", days);
      body.put("period_interval", 1);
      body.put("track_date_only", 1);
      body.put("rollover", 0);
      body.put("assignment_type", "no-assignment");
      body.put("consume_product_on_execution", 0);
      body.put("active", 1);
    } catch (Exception e) { Log.e(TAG, "save: ", e); showErrorMessage(); return; }
    dlHelper.post(grocyApi.getObjects(ENTITY.CHORES), body,
        r -> sendEvent(Event.NAVIGATE_UP),
        error -> { Log.e(TAG, "save: " + error); showNetworkErrorMessage(error); });
  }

  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }
  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
