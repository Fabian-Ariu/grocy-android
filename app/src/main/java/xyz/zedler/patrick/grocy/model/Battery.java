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

package xyz.zedler.patrick.grocy.model;

import androidx.annotation.NonNull;
import org.json.JSONObject;

/** Akku/Batterie aus /api/batteries (Objekt + Ladezustand). */
public class Battery {

  public final int id;
  public final String name;
  public final String description;
  public final String usedIn;
  public final int chargeIntervalDays;
  public final String lastTrackedTime;
  public final String nextEstimatedChargeTime;

  public Battery(@NonNull JSONObject entry) {
    JSONObject b = entry.optJSONObject("battery");
    if (b == null) {
      b = entry;
    }
    id = b.optInt("id");
    name = b.optString("name", "");
    description = b.optString("description", "");
    usedIn = b.optString("used_in", "");
    chargeIntervalDays = b.optInt("charge_interval_days", 0);
    lastTrackedTime = nullIfEmpty(entry.optString("last_tracked_time", null));
    nextEstimatedChargeTime = nullIfEmpty(entry.optString("next_estimated_charge_time", null));
  }

  private static String nullIfEmpty(String s) {
    return s == null || s.isEmpty() || s.equals("null") ? null : s;
  }
}
