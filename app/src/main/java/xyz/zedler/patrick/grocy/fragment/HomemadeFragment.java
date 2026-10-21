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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: homemade)
 */

package xyz.zedler.patrick.grocy.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentHomemadeBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.Recipe;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.util.UiUtil;
import xyz.zedler.patrick.grocy.viewmodel.HomemadeViewModel;

/** Selbstgemacht (Vorlage 05/06): Kategorie, Produkt, Glaeser, Ort, Rezept, Etiketten -> einlagern. */
public class HomemadeFragment extends BaseFragment {

  private MainActivity activity;
  private FragmentHomemadeBinding binding;
  private HomemadeViewModel viewModel;
  private int category = 0, jars = 4, locationIndex = 0; private boolean labels = true; private Recipe recipe;

  @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) { binding = FragmentHomemadeBinding.inflate(i, c, false); return binding.getRoot(); }
  @Override public void onDestroyView() { super.onDestroyView(); binding = null; }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(HomemadeViewModel.class);
    SystemBarBehavior sbb = new SystemBarBehavior(activity);
    sbb.setAppBar(binding.appBar); sbb.setContainer(binding.container); sbb.setScroll(binding.scroll, binding.linearContainer);
    sbb.applyAppBarInsetOnContainer(false); sbb.applyStatusBarInsetOnContainer(false); sbb.setUp();
    activity.setSystemBarBehavior(sbb);
    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());
    View[] cats = {binding.cat0, binding.cat1, binding.cat2, binding.cat3};
    for (int i = 0; i < cats.length; i++) { final int idx = i; cats[i].setOnClickListener(v -> { category = idx; render(); }); }
    binding.buttonMinus.setOnClickListener(v -> { if (jars > 1) jars--; render(); });
    binding.buttonPlus.setOnClickListener(v -> { jars++; render(); });
    binding.rowLocation.setOnClickListener(v -> { List<Location> l = viewModel.getLocationsLive().getValue(); if (l != null && !l.isEmpty()) { locationIndex = (locationIndex + 1) % l.size(); render(); } });
    binding.rowLabel.setOnClickListener(v -> { labels = !labels; render(); });
    binding.rowRecipe.setOnClickListener(v -> pickRecipe());
    binding.buttonSave.setOnClickListener(v -> viewModel.save(binding.editName.getText().toString(), category, jars, currentLocation(), recipe == null ? null : recipe.getId(), labels));
    viewModel.getHomemadeProductsLive().observe(getViewLifecycleOwner(), this::renderSuggestions);
    viewModel.getLocationsLive().observe(getViewLifecycleOwner(), l -> render());
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
      else if (event.getType() == Event.NAVIGATE_UP) activity.navUtil.navigateUp();
    });
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
    viewModel.loadFromDatabase();
    render();
  }

  private Location currentLocation() { List<Location> l = viewModel.getLocationsLive().getValue(); return l == null || l.isEmpty() ? null : l.get(Math.min(locationIndex, l.size() - 1)); }

  private void renderSuggestions(List<Product> products) {
    if (binding == null) return;
    binding.linearSuggestions.removeAllViews();
    if (products == null) return;
    for (Product p : products) {
      TextView t = new TextView(activity); t.setText(p.getName()); t.setTextSize(14); t.setTextColor(ResUtil.getColor(activity, R.attr.colorOnSurface));
      t.setBackgroundResource(R.drawable.glass_pill); int ph = UiUtil.dpToPx(activity, 16), pv = UiUtil.dpToPx(activity, 9); t.setPadding(ph, pv, ph, pv);
      ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMarginEnd(UiUtil.dpToPx(activity, 8)); t.setLayoutParams(lp);
      t.setOnClickListener(v -> binding.editName.setText(p.getName()));
      binding.linearSuggestions.addView(t);
    }
  }

  private void pickRecipe() {
    List<Recipe> recipes = viewModel.getRecipesLive().getValue();
    if (recipes == null || recipes.isEmpty()) { activity.showSnackbar(new SnackbarMessage(getString(R.string.hm_no_recipes)).getSnackbar(activity.binding.coordinatorMain)); return; }
    String[] names = new String[recipes.size() + 1]; names[0] = getString(R.string.hm_recipe_none);
    for (int i = 0; i < recipes.size(); i++) names[i + 1] = recipes.get(i).getName();
    new MaterialAlertDialogBuilder(activity).setTitle(R.string.hm_recipe).setItems(names, (d, w) -> { recipe = w == 0 ? null : recipes.get(w - 1); render(); }).show();
  }

  private void render() {
    if (binding == null) return;
    View[] cats = {binding.cat0, binding.cat1, binding.cat2, binding.cat3};
    for (int i = 0; i < cats.length; i++) cats[i].setBackgroundResource(i == category ? R.drawable.card_selected : R.drawable.glass_card);
    binding.textCount.setText(String.valueOf(jars));
    Location loc = currentLocation();
    binding.textLocation.setText(loc == null ? getString(R.string.hm_location_none) : loc.getName());
    int days = HomemadeViewModel.CATEGORY_DAYS[category];
    binding.textDue.setText(getString(R.string.hm_due_auto, new DateUtil(activity).getLocalizedDate(DateUtil.getTodayWithDaysAdded(days), DateUtil.FORMAT_MEDIUM)));
    binding.textRecipe.setText(recipe == null ? getString(R.string.hm_recipe_none) : recipe.getName());
    binding.toggleLabel.setBackgroundResource(labels ? R.drawable.toggle_on : R.drawable.toggle_off);
    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) binding.toggleKnob.getLayoutParams(); lp.gravity = (labels ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL; binding.toggleKnob.setLayoutParams(lp);
    binding.buttonSave.setText(getResources().getQuantityString(labels ? R.plurals.hm_save_labels : R.plurals.hm_save_n, jars, jars));
  }
}
