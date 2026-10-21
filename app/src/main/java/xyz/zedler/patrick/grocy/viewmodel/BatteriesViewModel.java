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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.Battery;

/** Akkus (Vorlage 23): Liste aus /batteries, "Geladen" = POST /batteries/{id}/charge. */
public class BatteriesViewModel extends BaseViewModel {

  private static final String TAG = BatteriesViewModel.class.getSimpleName();

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<List<Battery>> batteriesLive;

  public BatteriesViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    batteriesLive = new MutableLiveData<>();
  }

  public void downloadData() {
    dlHelper.get(grocyApi.getBatteries(), TAG, response -> {
      List<Battery> list = new ArrayList<>();
      try {
        JSONArray array = new JSONArray(response);
        for (int i = 0; i < array.length(); i++) {
          JSONObject entry = array.getJSONObject(i);
          JSONObject b = entry.optJSONObject("battery");
          if (b != null && b.optInt("active", 1) == 0) {
            continue;
          }
          list.add(new Battery(entry));
        }
      } catch (Exception e) {
        Log.e(TAG, "downloadData: ", e);
        showErrorMessage();
      }
      batteriesLive.setValue(list);
    }, error -> {
      Log.e(TAG, "downloadData: " + error);
      showErrorMessage();
      batteriesLive.setValue(new ArrayList<>());
    });
  }

  public void charge(Battery battery) {
    JSONObject body = new JSONObject();
    try {
      String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
      body.put("tracked_time", now);
    } catch (Exception e) {
      Log.e(TAG, "charge: ", e);
    }
    dlHelper.post(grocyApi.chargeBattery(battery.id), body,
        response -> downloadData(),
        error -> {
          Log.e(TAG, "charge: " + error);
          showErrorMessage();
        }
    );
  }

  public MutableLiveData<List<Battery>> getBatteriesLive() {
    return batteriesLive;
  }

  public MutableLiveData<Boolean> getIsLoadingLive() {
    return isLoadingLive;
  }

  @Override
  protected void onCleared() {
    dlHelper.destroy();
    super.onCleared();
  }
}
