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
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentEquipmentBinding;
import xyz.zedler.patrick.grocy.databinding.RowEquipmentBinding;
import xyz.zedler.patrick.grocy.databinding.RowMaintenanceBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.util.UiUtil;
import xyz.zedler.patrick.grocy.viewmodel.EquipmentViewModel;
import xyz.zedler.patrick.grocy.viewmodel.EquipmentViewModel.Equipment;
import xyz.zedler.patrick.grocy.viewmodel.EquipmentViewModel.Maintenance;

/** Geraete (Vorlage 21): Grocy-Equipment mit Wartungen (Chores), erledigen und anlegen. */
public class EquipmentFragment extends BaseFragment {

  private MainActivity activity;
  private FragmentEquipmentBinding binding;
  private EquipmentViewModel viewModel;
  private final Set<Integer> expanded = new HashSet<>();

  @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) {
    binding = FragmentEquipmentBinding.inflate(i, c, false); return binding.getRoot();
  }
  @Override public void onDestroyView() { super.onDestroyView(); binding = null; }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(EquipmentViewModel.class);
    SystemBarBehavior sbb = new SystemBarBehavior(activity);
    sbb.setAppBar(binding.appBar); sbb.setContainer(binding.swipe); sbb.setScroll(binding.scroll, binding.linearContainer);
    sbb.applyAppBarInsetOnContainer(false); sbb.applyStatusBarInsetOnContainer(false); sbb.setUp();
    activity.setSystemBarBehavior(sbb);
    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());
    binding.swipe.setOnRefreshListener(() -> viewModel.downloadData());
    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(), l -> { if (binding != null) binding.swipe.setRefreshing(l != null && l); });
    viewModel.getEquipmentLive().observe(getViewLifecycleOwner(), e -> render());
    viewModel.getMaintenanceLive().observe(getViewLifecycleOwner(), m -> render());
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
    });
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(true, R.menu.menu_empty);
    activity.updateFab(R.drawable.ic_round_add_anim, R.string.equipment_add, "equipment_add", savedInstanceState == null, this::askNewEquipment);
    viewModel.downloadData();
  }

  private void askNewEquipment() {
    EditText input = new EditText(activity);
    input.setHint(R.string.equipment_name_hint);
    int p = UiUtil.dpToPx(activity, 20);
    FrameLayout wrap = new FrameLayout(activity); wrap.setPadding(p, 0, p, 0); wrap.addView(input);
    new MaterialAlertDialogBuilder(activity).setTitle(R.string.equipment_add).setView(wrap)
        .setPositiveButton(R.string.action_save, (d, w) -> { String n = input.getText().toString().trim(); if (!n.isEmpty()) viewModel.createEquipment(n, null); })
        .setNegativeButton(R.string.action_cancel, null).show();
  }

  private void render() {
    if (binding == null) return;
    List<Equipment> list = viewModel.getEquipmentLive().getValue();
    Map<Integer, List<Maintenance>> maint = viewModel.getMaintenanceLive().getValue();
    binding.linearContainer.removeAllViews();
    if (list == null || list.isEmpty()) { binding.linearContainer.addView(binding.textEmpty); binding.textEmpty.setVisibility(View.VISIBLE); return; }
    DateUtil dateUtil = new DateUtil(activity);
    int colorWarn = ResUtil.getColor(activity, R.attr.colorTertiary);
    int colorOk = ResUtil.getColor(activity, R.attr.colorOnSurfaceVariant);
    for (Equipment e : list) {
      RowEquipmentBinding row = RowEquipmentBinding.inflate(getLayoutInflater(), binding.linearContainer, false);
      row.name.setText(e.name);
      List<Maintenance> ms = maint == null ? null : maint.get(e.id);
      String dueText = null; boolean warn = false;
      if (ms != null) for (Maintenance m : ms) if (m.nextDue != null) { int d = DateUtil.getDaysFromNow(m.nextDue); if (dueText == null || d <= 0) { dueText = m.name + ": " + dateUtil.getHumanForDaysFromNow(m.nextDue); warn = d <= 0; } }
      row.subtitle.setText(dueText != null ? (e.description.isEmpty() ? dueText : e.description + " \u00b7 " + dueText) : e.description);
      row.subtitle.setTextColor(warn ? colorWarn : colorOk);
      boolean open = expanded.contains(e.id);
      row.details.setVisibility(open ? View.VISIBLE : View.GONE);
      row.chevron.setRotation(open ? 180 : 0);
      row.header.setOnClickListener(v -> { if (expanded.contains(e.id)) expanded.remove(e.id); else expanded.add(e.id); render(); });
      if (!e.manual.isEmpty()) { row.manual.setText(getString(R.string.equipment_manual, e.manual)); row.manual.setVisibility(View.VISIBLE); }
      row.containerMaintenance.removeAllViews();
      if (ms == null || ms.isEmpty()) row.textNone.setVisibility(View.VISIBLE);
      else for (Maintenance m : ms) {
        RowMaintenanceBinding mr = RowMaintenanceBinding.inflate(getLayoutInflater(), row.containerMaintenance, false);
        mr.name.setText(m.name);
        mr.interval.setText(m.periodDays > 0 ? getString(R.string.maint_interval, m.periodDays) : getString(R.string.maint_manual));
        if (m.nextDue != null) { int d = DateUtil.getDaysFromNow(m.nextDue); mr.due.setText(dateUtil.getHumanForDaysFromNow(m.nextDue)); mr.due.setTextColor(d <= 0 ? colorWarn : colorOk); }
        else mr.due.setVisibility(View.GONE);
        mr.buttonDone.setOnClickListener(v -> viewModel.execute(m));
        row.containerMaintenance.addView(mr.getRoot());
      }
      row.buttonAdd.setOnClickListener(v -> {
        Bundle b = new Bundle(); b.putInt("equipmentId", e.id); b.putString("equipmentName", e.name);
        activity.navUtil.navigate(R.id.maintenanceEditFragment, b);
      });
      binding.linearContainer.addView(row.getRoot());
    }
  }
}
