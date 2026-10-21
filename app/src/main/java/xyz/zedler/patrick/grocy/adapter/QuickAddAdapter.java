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
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.databinding.RowQuickAddBinding;
import xyz.zedler.patrick.grocy.model.Product;

public class QuickAddAdapter extends RecyclerView.Adapter<QuickAddAdapter.ViewHolder> {

  public interface Listener {
    void onTap(Product product);
    void onLongTap(Product product);
  }

  private final Context context;
  private final Listener listener;
  private final List<Product> items = new ArrayList<>();
  private Map<Integer, Integer> counts = new HashMap<>();

  public QuickAddAdapter(Context context, Listener listener) {
    this.context = context;
    this.listener = listener;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    final RowQuickAddBinding binding;
    ViewHolder(RowQuickAddBinding b) { super(b.getRoot()); binding = b; }
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(RowQuickAddBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder h, int position) {
    Product p = items.get(position);
    h.binding.name.setText(p.getName());
    int days = p.getDefaultDueDaysInt();
    String daysText = days > 0
        ? context.getResources().getQuantityString(R.plurals.quick_add_days, days, days)
        : context.getString(R.string.quick_add_no_due);
    h.binding.days.setText(daysText);
    Integer c = counts.get(p.getId());
    boolean sel = c != null && c > 0;
    h.binding.badge.setVisibility(sel ? View.VISIBLE : View.GONE);
    if (sel) h.binding.badge.setText(String.valueOf(c));
    h.binding.card.setBackgroundResource(sel ? R.drawable.card_selected : R.drawable.glass_card);
    h.binding.card.setOnClickListener(v -> listener.onTap(p));
    h.binding.card.setOnLongClickListener(v -> { listener.onLongTap(p); return true; });
  }

  @Override
  public int getItemCount() { return items.size(); }

  public void updateData(List<Product> products, Map<Integer, Integer> newCounts) {
    items.clear(); items.addAll(products);
    counts = newCounts == null ? new HashMap<>() : newCounts;
    notifyDataSetChanged();
  }
}
