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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.databinding.RowReceiptLineBinding;
import xyz.zedler.patrick.grocy.util.NumUtil;
import xyz.zedler.patrick.grocy.viewmodel.ReceiptViewModel.Line;

public class ReceiptLineAdapter extends RecyclerView.Adapter<ReceiptLineAdapter.ViewHolder> {

  public interface Listener { void onToggle(Line line); void onAssign(Line line); }

  private final Listener listener;
  private final List<Line> items = new ArrayList<>();

  public ReceiptLineAdapter(Listener listener) { this.listener = listener; }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    final RowReceiptLineBinding binding;
    ViewHolder(RowReceiptLineBinding b) { super(b.getRoot()); binding = b; }
  }

  @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(RowReceiptLineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override public void onBindViewHolder(@NonNull ViewHolder h, int position) {
    Line l = items.get(position);
    RowReceiptLineBinding b = h.binding;
    boolean unknown = l.product == null && !l.deposit;
    b.name.setText(l.deposit ? b.getRoot().getContext().getString(R.string.receipt_deposit) : unknown ? b.getRoot().getContext().getString(R.string.receipt_unknown) : l.name);
    b.name.setTextColor(b.getRoot().getContext().getColor(unknown ? R.color.receipt_unknown : l.deposit ? R.color.receipt_muted : R.color.receipt_text));
    b.raw.setText(l.raw == null ? "" : l.raw.toUpperCase(Locale.ROOT));
    String qty = NumUtil.trimAmount(l.qty, 2) + (l.unitText != null && !l.unitText.equals("x") ? " " + l.unitText : "");
    b.qty.setText(qty); b.qty.setVisibility(l.deposit ? View.GONE : View.VISIBLE);
    b.price.setText(l.price == null ? "" : String.format(Locale.GERMANY, "%.2f", l.price));
    b.buttonAssign.setVisibility(unknown ? View.VISIBLE : View.GONE);
    b.check.setOnCheckedChangeListener(null);
    b.check.setChecked(l.checked);
    b.check.setEnabled(!l.deposit);
    b.check.setOnCheckedChangeListener((v, c) -> listener.onToggle(l));
    b.buttonAssign.setOnClickListener(v -> listener.onAssign(l));
    b.getRoot().setOnClickListener(v -> { if (unknown) listener.onAssign(l); else listener.onToggle(l); });
    b.getRoot().setAlpha(l.deposit ? 0.6f : 1f);
    b.divider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);
  }

  @Override public int getItemCount() { return items.size(); }

  public void updateData(List<Line> lines) { items.clear(); if (lines != null) items.addAll(lines); notifyDataSetChanged(); }
}
