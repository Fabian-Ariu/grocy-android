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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.BatteryAdapter;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentBatteriesBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.viewmodel.BatteriesViewModel;

/** Akkus (Vorlage 23): echte Grocy-Batterien, "Geladen" protokolliert eine Ladung. */
public class BatteriesFragment extends BaseFragment implements BatteryAdapter.BatteryListener {

  private MainActivity activity;
  private FragmentBatteriesBinding binding;
  private BatteriesViewModel viewModel;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
  ) {
    binding = FragmentBatteriesBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (binding != null) {
      binding.recycler.setAdapter(null);
      binding = null;
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(BatteriesViewModel.class);

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.swipe);
    systemBarBehavior.setRecycler(binding.recycler);
    systemBarBehavior.applyAppBarInsetOnContainer(false);
    systemBarBehavior.applyStatusBarInsetOnContainer(false);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());

    binding.recycler.setLayoutManager(new LinearLayoutManager(activity));
    BatteryAdapter adapter = new BatteryAdapter(requireContext(), this);
    binding.recycler.setAdapter(adapter);

    binding.swipe.setOnRefreshListener(() -> viewModel.downloadData());
    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(), loading -> {
      if (binding != null) binding.swipe.setRefreshing(loading != null && loading);
    });
    viewModel.getBatteriesLive().observe(getViewLifecycleOwner(), items -> {
      if (items == null || binding == null) return;
      adapter.updateData(items);
      binding.textEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    });
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
      }
    });

    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.recycler, true, true);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);

    viewModel.downloadData();
  }

  @Override
  public void onCharged(xyz.zedler.patrick.grocy.model.Battery battery) {
    viewModel.charge(battery);
  }
}
