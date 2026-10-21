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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.api.GrocyApi.ENTITY;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.util.DateUtil;

/** Geraete (Vorlage 21/22): Grocy-Equipment + Wartungen als Chores.
 *  Verknuepfung: Chore-Beschreibung enthaelt das Tag "#geraet:<id>". */
public class EquipmentViewModel extends BaseViewModel {

  private static final String TAG = EquipmentViewModel.class.getSimpleName();
  public static final String TAG_PREFIX = "#geraet:";

  public static class Equipment {
    public final int id; public final String name; public final String description; public final String manual;
    Equipment(JSONObject o) {
      id = o.optInt("id"); name = str(o, "name"); description = str(o, "description"); manual = str(o, "instruction_manual_file_name");
    }
  }
  /** Grocy liefert JSON-null; optString gaebe "null" zurueck. */
  private static String str(JSONObject o, String key) {
    if (o == null || o.isNull(key)) return "";
    String v = o.optString(key, ""); return v.equals("null") ? "" : v;
  }
  public static class Maintenance {
    public final int choreId; public final String name; public final int periodDays; public final String periodType;
    public final String nextDue; public final String lastTracked; public final int equipmentId;
    Maintenance(int choreId, String name, int periodDays, String periodType, String nextDue, String lastTracked, int equipmentId) {
      this.choreId = choreId; this.name = name; this.periodDays = periodDays; this.periodType = periodType;
      this.nextDue = nextDue; this.lastTracked = lastTracked; this.equipmentId = equipmentId;
    }
  }

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final DateUtil dateUtil;
  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<List<Equipment>> equipmentLive;
  private final MutableLiveData<Map<Integer, List<Maintenance>>> maintenanceLive;

  public EquipmentViewModel(@NonNull Application application) {
    super(application);
    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    dateUtil = new DateUtil(getApplication());
    equipmentLive = new MutableLiveData<>(new ArrayList<>());
    maintenanceLive = new MutableLiveData<>(new HashMap<>());
  }

  public void downloadData() {
    dlHelper.get(grocyApi.getObjects("equipment"), TAG, response -> {
      List<Equipment> list = new ArrayList<>();
      try {
        JSONArray a = new JSONArray(response);
        for (int i = 0; i < a.length(); i++) list.add(new Equipment(a.getJSONObject(i)));
      } catch (Exception e) { Log.e(TAG, "equipment: ", e); showErrorMessage(); }
      equipmentLive.setValue(list);
      loadChores();
    }, error -> { Log.e(TAG, "equipment: " + error); showNetworkErrorMessage(error); equipmentLive.setValue(new ArrayList<>()); });
  }

  private void loadChores() {
    dlHelper.get(grocyApi.getObjects(ENTITY.CHORES), TAG, defs -> {
      Map<Integer, JSONObject> defById = new HashMap<>();
      try {
        JSONArray a = new JSONArray(defs);
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); defById.put(o.optInt("id"), o); }
      } catch (Exception e) { Log.e(TAG, "chores defs: ", e); }
      dlHelper.get(grocyApi.getChores(), TAG, entries -> {
        Map<Integer, List<Maintenance>> map = new HashMap<>();
        try {
          JSONArray a = new JSONArray(entries);
          for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.getJSONObject(i);
            int choreId = e.optInt("chore_id");
            JSONObject d = defById.get(choreId);
            if (d == null || d.optInt("active", 1) == 0) continue;
            int eqId = parseEquipmentId(d.optString("description", ""));
            if (eqId <= 0) continue;
            Maintenance m = new Maintenance(choreId, d.optString("name", e.optString("chore_name", "")),
                d.optInt("period_days", 0), d.optString("period_type", ""),
                nz(e.optString("next_estimated_execution_time", null)), nz(e.optString("last_tracked_time", null)), eqId);
            List<Maintenance> l = map.get(eqId);
            if (l == null) { l = new ArrayList<>(); map.put(eqId, l); }
            l.add(m);
          }
        } catch (Exception ex) { Log.e(TAG, "chores: ", ex); }
        maintenanceLive.setValue(map);
      }, error -> { Log.e(TAG, "chores: " + error); maintenanceLive.setValue(new HashMap<>()); });
    }, error -> { Log.e(TAG, "chore defs: " + error); maintenanceLive.setValue(new HashMap<>()); });
  }

  private static String nz(String s) { return s == null || s.isEmpty() || s.equals("null") ? null : s; }

  public static int parseEquipmentId(String description) {
    if (description == null) return -1;
    int i = description.indexOf(TAG_PREFIX);
    if (i < 0) return -1;
    int j = i + TAG_PREFIX.length(); StringBuilder sb = new StringBuilder();
    while (j < description.length() && Character.isDigit(description.charAt(j))) sb.append(description.charAt(j++));
    try { return sb.length() == 0 ? -1 : Integer.parseInt(sb.toString()); } catch (Exception e) { return -1; }
  }

  /** Wartung erledigt: Chore-Ausfuehrung protokollieren. */
  public void execute(Maintenance m) {
    JSONObject body = new JSONObject();
    try { body.put("tracked_time", dateUtil.getCurrentDateWithTimeStr()); body.put("skipped", false); }
    catch (Exception e) { Log.e(TAG, "execute: ", e); }
    dlHelper.post(grocyApi.executeChore(m.choreId), body, r -> downloadData(),
        error -> { Log.e(TAG, "execute: " + error); showNetworkErrorMessage(error); });
  }

  /** Neues Geraet (Grocy-Equipment). */
  public void createEquipment(String name, Runnable onDone) {
    JSONObject body = new JSONObject();
    try { body.put("name", name); body.put("description", ""); } catch (Exception e) { Log.e(TAG, "createEquipment: ", e); }
    dlHelper.post(grocyApi.getObjects("equipment"), body, r -> { downloadData(); if (onDone != null) onDone.run(); },
        error -> { Log.e(TAG, "createEquipment: " + error); showNetworkErrorMessage(error); });
  }

  public MutableLiveData<List<Equipment>> getEquipmentLive() { return equipmentLive; }
  public MutableLiveData<Map<Integer, List<Maintenance>>> getMaintenanceLive() { return maintenanceLive; }
  public MutableLiveData<Boolean> getIsLoadingLive() { return isLoadingLive; }

  @Override protected void onCleared() { dlHelper.destroy(); super.onCleared(); }
}
