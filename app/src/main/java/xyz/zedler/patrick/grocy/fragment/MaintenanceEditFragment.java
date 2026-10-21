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

package xyz.zedler.patrick.grocy.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentMaintenanceEditBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.util.UiUtil;
import xyz.zedler.patrick.grocy.viewmodel.MaintenanceEditViewModel;

/** Wartung anlegen (Vorlage 22). */
public class MaintenanceEditFragment extends BaseFragment {

  private MainActivity activity;
  private FragmentMaintenanceEditBinding binding;
  private MaintenanceEditViewModel viewModel;
  private int equipmentId; private String equipmentName = "";
  private int count = 90; private int unit = 0; /* 0 Tage, 1 Wochen, 2 Monate */ private boolean afterLast = true;

  @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) {
    binding = FragmentMaintenanceEditBinding.inflate(i, c, false); return binding.getRoot();
  }
  @Override public void onDestroyView() { super.onDestroyView(); binding = null; }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(MaintenanceEditViewModel.class);
    Bundle args = getArguments();
    if (args != null) { equipmentId = args.getInt("equipmentId", -1); equipmentName = args.getString("equipmentName", ""); }
    SystemBarBehavior sbb = new SystemBarBehavior(activity);
    sbb.setAppBar(binding.appBar); sbb.setContainer(binding.container); sbb.setScroll(binding.scroll, binding.linearContainer);
    sbb.applyAppBarInsetOnContainer(false); sbb.applyStatusBarInsetOnContainer(false); sbb.setUp();
    activity.setSystemBarBehavior(sbb);
    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());
    binding.textEquipment.setText(equipmentName);
    String[] suggestions = getResources().getStringArray(R.array.maint_suggestions);
    for (String s : suggestions) {
      TextView t = new TextView(activity); t.setText(s); t.setTextSize(14); t.setTextColor(ResUtil.getColor(activity, R.attr.colorOnSurface));
      t.setBackgroundResource(R.drawable.glass_pill); int ph = UiUtil.dpToPx(activity, 16), pv = UiUtil.dpToPx(activity, 9); t.setPadding(ph, pv, ph, pv);
      ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMarginEnd(UiUtil.dpToPx(activity, 8)); t.setLayoutParams(lp);
      t.setOnClickListener(v -> binding.editName.setText(s));
      binding.linearSuggestions.addView(t);
    }
    binding.tabAfterLast.setOnClickListener(v -> { afterLast = true; renderTabs(); });
    binding.tabFixed.setOnClickListener(v -> { afterLast = false; renderTabs(); });
    binding.buttonMinus.setOnClickListener(v -> { if (count > 1) count--; renderCount(); });
    binding.buttonPlus.setOnClickListener(v -> { count++; renderCount(); });
    binding.unitDays.setOnClickListener(v -> { unit = 0; renderUnits(); });
    binding.unitWeeks.setOnClickListener(v -> { unit = 1; renderUnits(); });
    binding.unitMonths.setOnClickListener(v -> { unit = 2; renderUnits(); });
    binding.buttonSave.setOnClickListener(v -> {
      String name = binding.editName.getText().toString().trim();
      if (name.isEmpty()) { binding.editName.setError(getString(R.string.maint_name_hint)); return; }
      viewModel.save(equipmentId, equipmentName, name, days(), afterLast, binding.editNote.getText().toString().trim());
    });
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
      else if (event.getType() == Event.NAVIGATE_UP) { activity.showSnackbar(new SnackbarMessage(getString(R.string.maint_saved)).getSnackbar(activity.binding.coordinatorMain)); activity.navUtil.navigateUp(); }
    });
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
    renderTabs(); renderUnits(); renderCount();
  }

  private int days() { return unit == 0 ? count : unit == 1 ? count * 7 : count * 30; }
  private void renderTabs() {
    binding.tabAfterLast.setBackgroundResource(afterLast ? R.drawable.white_chip : 0);
    binding.tabFixed.setBackgroundResource(afterLast ? 0 : R.drawable.white_chip);
    binding.tabAfterLast.setTextColor(ResUtil.getColor(activity, afterLast ? R.attr.colorOnSurface : R.attr.colorOnSurfaceVariant));
    binding.tabFixed.setTextColor(ResUtil.getColor(activity, afterLast ? R.attr.colorOnSurfaceVariant : R.attr.colorOnSurface));
    renderExplain();
  }
  private void renderUnits() {
    TextView[] t = {binding.unitDays, binding.unitWeeks, binding.unitMonths};
    for (int i = 0; i < 3; i++) { t[i].setBackgroundResource(i == unit ? R.drawable.chip_blue_selected : R.drawable.glass_pill); t[i].setTextColor(ResUtil.getColor(activity, i == unit ? R.attr.colorPrimary : R.attr.colorOnSurface)); }
    renderExplain();
  }
  private void renderCount() { binding.textCount.setText(String.valueOf(count)); renderExplain(); }
  private void renderExplain() {
    String name = binding.editName.getText().toString().trim();
    String[] units = {getString(R.string.maint_unit_days), getString(R.string.maint_unit_weeks), getString(R.string.maint_unit_months)};
    binding.textExplain.setText(getString(afterLast ? R.string.maint_explain_after_last : R.string.maint_explain_fixed,
        name.isEmpty() ? getString(R.string.maint_title) : name, count, units[unit]));
  }
}
