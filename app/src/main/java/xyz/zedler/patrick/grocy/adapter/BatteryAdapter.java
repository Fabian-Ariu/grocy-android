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

package xyz.zedler.patrick.grocy.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.databinding.RowBatteryBinding;
import xyz.zedler.patrick.grocy.model.Battery;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.ResUtil;

public class BatteryAdapter extends RecyclerView.Adapter<BatteryAdapter.ViewHolder> {

  public interface BatteryListener {
    void onCharged(Battery battery);
  }

  private final Context context;
  private final BatteryListener listener;
  private final DateUtil dateUtil;
  private final List<Battery> items = new ArrayList<>();

  public BatteryAdapter(Context context, BatteryListener listener) {
    this.context = context;
    this.listener = listener;
    this.dateUtil = new DateUtil(context);
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    final RowBatteryBinding binding;
    ViewHolder(RowBatteryBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(RowBatteryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    Battery b = items.get(position);
    holder.binding.name.setText(b.name);
    String sub = b.chargeIntervalDays > 0
        ? context.getString(R.string.batteries_interval, b.chargeIntervalDays)
        : context.getString(R.string.batteries_no_interval);
    if (!b.usedIn.isEmpty()) {
      sub = sub + " \u00b7 " + b.usedIn;
    }
    holder.binding.subtitle.setText(sub);
    int colorOk = ResUtil.getColor(context, R.attr.colorOnSurfaceVariant);
    int colorWarn = ResUtil.getColor(context, R.attr.colorTertiary);
    if (b.nextEstimatedChargeTime != null) {
      int days = DateUtil.getDaysFromNow(b.nextEstimatedChargeTime);
      holder.binding.status.setText(dateUtil.getHumanForDaysFromNow(b.nextEstimatedChargeTime));
      holder.binding.status.setTextColor(days <= 7 ? colorWarn : colorOk);
      holder.binding.fill.setBackgroundResource(days <= 7 ? R.drawable.batt_fill_orange : R.drawable.batt_fill_green);
      holder.binding.fill.getLayoutParams().width = (int) (context.getResources().getDisplayMetrics().density
          * (days < 0 ? 4 : days <= 7 ? 8 : days <= 30 ? 18 : 26));
      holder.binding.fill.requestLayout();
      holder.binding.status.setVisibility(android.view.View.VISIBLE);
    } else {
      holder.binding.status.setVisibility(android.view.View.GONE);
    }
    holder.binding.buttonCharged.setOnClickListener(v -> listener.onCharged(b));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  public void updateData(List<Battery> newItems) {
    items.clear();
    items.addAll(newItems);
    notifyDataSetChanged();
  }
}
