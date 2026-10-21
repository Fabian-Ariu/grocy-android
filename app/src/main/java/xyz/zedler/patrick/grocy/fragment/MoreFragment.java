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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: "Mehr" screen)
 */

package xyz.zedler.patrick.grocy.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentMoreBinding;
import xyz.zedler.patrick.grocy.util.ClickUtil;

/** Tab "Mehr" (Vorlage 12): Vollbild-Uebersicht mit echten Zielen statt Schubladen-Sheet. */
public class MoreFragment extends BaseFragment {

  private MainActivity activity;
  private FragmentMoreBinding binding;
  private ClickUtil clickUtil;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
  ) {
    binding = FragmentMoreBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    clickUtil = new ClickUtil();

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.container);
    systemBarBehavior.setScroll(binding.scroll, binding.linearContainer);
    systemBarBehavior.applyAppBarInsetOnContainer(false);
    systemBarBehavior.applyStatusBarInsetOnContainer(false);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);

    binding.cardInventory.setOnClickListener(v -> go(R.string.deep_link_inventoryFragment));
    binding.cardMealPlan.setOnClickListener(v -> go(R.string.deep_link_mealPlanFragment));
    binding.cardChores.setOnClickListener(v -> go(R.string.deep_link_choresFragment));
    binding.cardTasks.setOnClickListener(v -> go(R.string.deep_link_tasksFragment));
    binding.cardMasterData.setOnClickListener(v -> go(R.string.deep_link_masterDataOverviewFragment));
    binding.cardShoppingMode.setOnClickListener(v -> go(R.string.deep_link_shoppingModeFragment));
    binding.cardSettings.setOnClickListener(v -> go(R.string.deep_link_settingsFragment));
    binding.cardBatteries.setOnClickListener(v -> {
      if (clickUtil.isDisabled()) return;
      activity.navUtil.navigate(R.id.batteriesFragment);
    });
    binding.rowAbout.setOnClickListener(v -> go(R.string.deep_link_aboutFragment));
    binding.rowHelp.setOnClickListener(v -> {
      if (clickUtil.isDisabled()) return;
      activity.showHelpBottomSheet();
    });
    binding.rowFeedback.setOnClickListener(v -> {
      if (clickUtil.isDisabled()) return;
      activity.showFeedbackBottomSheet();
    });
  }

  private void go(int deepLink) {
    if (clickUtil.isDisabled()) return;
    activity.navUtil.navigateDeepLink(getString(deepLink));
  }
}
