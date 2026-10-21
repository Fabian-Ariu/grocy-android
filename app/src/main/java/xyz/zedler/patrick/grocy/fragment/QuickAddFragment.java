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

package xyz.zedler.patrick.grocy.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import java.util.List;
import java.util.Map;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.QuickAddAdapter;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentQuickAddBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductGroup;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.util.UiUtil;
import xyz.zedler.patrick.grocy.viewmodel.QuickAddViewModel;

/** Schnellerfassung (Vorlage 04): Antippen = +1, lang = -1, dann alles einlagern. */
public class QuickAddFragment extends BaseFragment implements QuickAddAdapter.Listener {

  private MainActivity activity;
  private FragmentQuickAddBinding binding;
  private QuickAddViewModel viewModel;
  private QuickAddAdapter adapter;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentQuickAddBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (binding != null) { binding.recycler.setAdapter(null); binding = null; }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(QuickAddViewModel.class);

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.container);
    systemBarBehavior.setRecycler(binding.recycler);
    systemBarBehavior.applyAppBarInsetOnContainer(false);
    systemBarBehavior.applyStatusBarInsetOnContainer(false);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());
    binding.recycler.setLayoutManager(new GridLayoutManager(activity, 3));
    adapter = new QuickAddAdapter(requireContext(), this);
    binding.recycler.setAdapter(adapter);

    binding.editSearch.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
      @Override public void afterTextChanged(Editable s) { viewModel.setSearch(s.toString()); }
    });

    viewModel.getProductsLive().observe(getViewLifecycleOwner(), items ->
        adapter.updateData(items, viewModel.getCountsLive().getValue()));
    viewModel.getCountsLive().observe(getViewLifecycleOwner(), counts -> {
      List<Product> items = viewModel.getProductsLive().getValue();
      if (items != null) adapter.updateData(items, counts);
      renderSummary(counts);
    });
    viewModel.getGroupsLive().observe(getViewLifecycleOwner(), this::renderGroups);
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
      }
    });
    binding.buttonAdd.setOnClickListener(v -> viewModel.purchaseAll());

    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.recycler, true, true);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);

    viewModel.loadFromDatabase();
  }

  private void renderGroups(List<ProductGroup> groups) {
    if (binding == null || groups == null) return;
    binding.linearGroups.removeAllViews();
    addGroupPill(getString(R.string.quick_add_all), -1);
    for (ProductGroup g : groups) addGroupPill(g.getName(), g.getId());
  }

  private void addGroupPill(String label, int groupId) {
    TextView t = new TextView(activity);
    boolean sel = viewModel.getSelectedGroupId() == groupId;
    t.setText(label);
    t.setTextSize(14);
    t.setTextColor(sel ? ResUtil.getColor(activity, R.attr.colorSurface) : ResUtil.getColor(activity, R.attr.colorOnSurface));
    t.setBackgroundResource(sel ? R.drawable.chip_dark : R.drawable.glass_pill);
    int ph = UiUtil.dpToPx(activity, 16), pv = UiUtil.dpToPx(activity, 9);
    t.setPadding(ph, pv, ph, pv);
    ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMarginEnd(UiUtil.dpToPx(activity, 8));
    t.setLayoutParams(lp);
    t.setOnClickListener(v -> { viewModel.selectGroup(groupId); renderGroups(viewModel.getGroupsLive().getValue()); });
    binding.linearGroups.addView(t);
  }

  private void renderSummary(Map<Integer, Integer> counts) {
    if (binding == null) return;
    int total = viewModel.totalCount();
    binding.buttonAdd.setEnabled(total > 0);
    binding.buttonAdd.setAlpha(total > 0 ? 1f : 0.5f);
    binding.buttonAdd.setText(getResources().getQuantityString(R.plurals.quick_add_button_n, total, total));
    if (counts == null || counts.isEmpty()) { binding.textSummary.setVisibility(View.GONE); return; }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
      Product p = viewModel.productById(e.getKey());
      if (p == null) continue;
      if (sb.length() > 0) sb.append(" \u00b7 ");
      sb.append(p.getName()).append(' ').append(e.getValue());
    }
    binding.textSummary.setText(sb.toString());
    binding.textSummary.setVisibility(View.VISIBLE);
  }

  @Override public void onTap(Product product) { viewModel.increment(product); }
  @Override public void onLongTap(Product product) { viewModel.decrement(product); }
}
