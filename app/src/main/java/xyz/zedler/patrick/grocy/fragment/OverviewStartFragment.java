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
 */

package xyz.zedler.patrick.grocy.fragment;

import android.animation.LayoutTransition;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import xyz.zedler.patrick.grocy.BuildConfig;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.Constants.PREF;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentOverviewStartBinding;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.FeedbackBottomSheet;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.util.ClickUtil;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.util.UiUtil;
import xyz.zedler.patrick.grocy.util.ViewUtil;
import xyz.zedler.patrick.grocy.view.FormattedTextView;
import xyz.zedler.patrick.grocy.viewmodel.OverviewStartViewModel;

public class OverviewStartFragment extends BaseFragment {

  private final static String TAG = OverviewStartFragment.class.getSimpleName();

  private static final String DIALOG_FAB_INFO = "dialog_fab_info";

  private MainActivity activity;
  private FragmentOverviewStartBinding binding;
  private OverviewStartViewModel viewModel;
  private ClickUtil clickUtil;
  private AlertDialog dialogFabInfo;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentOverviewStartBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (binding != null) {
      binding = null;
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(OverviewStartViewModel.class);
    binding.setViewModel(viewModel);
    binding.setFragment(this);
    binding.setActivity(activity);
    binding.setLifecycleOwner(getViewLifecycleOwner());

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.swipe);
    systemBarBehavior.setScroll(binding.scroll, binding.constraint);
    systemBarBehavior.applyAppBarInsetOnContainer(false);
    systemBarBehavior.applyStatusBarInsetOnContainer(false);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

    binding.relative.post(() -> {
      LayoutTransition transition = new LayoutTransition();
      transition.enableTransitionType(LayoutTransition.CHANGING);
      binding.relative.setLayoutTransition(transition);
    });
    binding.linearContainer.post(() -> {
      LayoutTransition transition = new LayoutTransition();
      transition.enableTransitionType(LayoutTransition.CHANGING);
      binding.linearContainer.setLayoutTransition(transition);
    });

    // Das alte Dashboard (Bestand-/Einkaufszettel-Karten mit Aktionsleisten) ist von der
    // Startseite entfernt; diese Bereiche haben eigene Tabs. Keine Scroll-Leisten mehr hier.

    clickUtil = new ClickUtil(1000);

    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(
            ((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain)
        );
      }
    });

    // Kein Toolbar-Menue mehr auf der Startseite (Vorlage): Einstellungen, Hilfe und
    // Feedback sind ueber den Tab "Mehr" erreichbar.

    if (savedInstanceState == null || !viewModel.isAlreadyLoadedFromDatabase()) {
      viewModel.loadFromDatabase(true);
    }

    // (DEBUG-Label der alten Titelleiste entfaellt mit der Titelleiste.)

    // UPDATE UI

    boolean animated = (getArguments() == null
        || getArguments().getBoolean(Constants.ARGUMENT.ANIMATED, true))
        && savedInstanceState == null;
    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.scroll);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    // Startseite: KEIN Scan-FAB. Der Scan-Hero auf der Seite ist die einzige
    // Scan-Flaeche (plus die Hardware-Seitentaste). Das beseitigt die dreifache
    // Scan-Redundanz (Hero + FAB + Tab).
    activity.updateBottomAppBar(false, R.menu.menu_empty);

    if (savedInstanceState != null && savedInstanceState.getBoolean(DIALOG_FAB_INFO)) {
      new Handler(Looper.getMainLooper()).postDelayed(
          this::showFabInfoDialogIfAppropriate, 1
      );
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    boolean isShowing = dialogFabInfo != null && dialogFabInfo.isShowing();
    outState.putBoolean(DIALOG_FAB_INFO, isShowing);
  }

  /** Scan-Hero (neues Design): Tippen startet den Scanner zum Verbrauchen,
   *  langes Druecken zum Einlagern. Gleiche Aktion wie der FAB. */
  public void startConsumeScan() {
    if (showFabInfoDialogIfAppropriate()) {
      return;
    }
    activity.navUtil.navigate(
        R.id.consumeFragment,
        new ConsumeFragmentArgs.Builder().setStartWithScanner(true).build().toBundle()
    );
  }

  public boolean startPurchaseScan() {
    activity.navUtil.navigate(
        R.id.purchaseFragment,
        new PurchaseFragmentArgs.Builder().setStartWithScanner(true).build().toBundle()
    );
    return true;
  }

  public void navigateToSettingsCatBehavior() {
    Bundle bundle = new SettingsFragmentArgs.Builder()
        .setShowCategory(Constants.SETTINGS.BEHAVIOR.class.getSimpleName())
        .build().toBundle();
    activity.navUtil.navigateDeepLink(R.string.deep_link_settingsFragment, bundle);
  }

  public void navigateToSettingsCatServer() {
    if (viewModel.getBeginnerModeEnabled()) {
      Bundle bundle = new SettingsFragmentArgs.Builder()
          .setShowCategory(Constants.SETTINGS.SERVER.class.getSimpleName())
          .build().toBundle();
      activity.navUtil.navigateDeepLink(R.string.deep_link_settingsFragment, bundle);
    } else {
      activity.navUtil.navigateDeepLink(getString(R.string.deep_link_settingsCatServerFragment));
    }
  }

  public void navigateToStoredPurchases() {
    if (viewModel.getBeginnerModeEnabled()) {
      Bundle bundle = new SettingsFragmentArgs.Builder()
          .setShowCategory(Constants.SETTINGS.SERVER.class.getSimpleName())
          .build().toBundle();
      activity.navUtil.navigateDeepLink(R.string.deep_link_settingsFragment, bundle);
    } else {
      activity.navUtil.navigateDeepLink(getString(R.string.deep_link_settingsCatServerFragment));
    }
  }

  private boolean showFabInfoDialogIfAppropriate() {
    if (viewModel.getOverviewFabInfoShown()) {
      return false;
    }
    FormattedTextView textView = new FormattedTextView(activity);
    textView.setTextColor(ResUtil.getColor(activity, R.attr.colorOnSurfaceVariant));
    textView.setTextSizeParagraph(14);
    textView.setBlockDistance(8);
    textView.setSideMargin(24);
    textView.setLastBlockWithBottomMargin(false);
    textView.setText(getString(R.string.msg_help_fab_overview_start));
    dialogFabInfo = new MaterialAlertDialogBuilder(
        activity, R.style.ThemeOverlay_Grocy_AlertDialog
    ).setTitle(R.string.title_help)
        .setView(textView)
        .setPositiveButton(R.string.title_consume, (dialog, which) -> {
          performHapticClick();
          viewModel.setOverviewFabInfoShown();
          activity.navUtil.navigate(
              R.id.consumeFragment,
              new ConsumeFragmentArgs.Builder()
                  .setStartWithScanner(true).build().toBundle()
          );
        }).setNegativeButton(R.string.title_purchase, (dialog, which) -> {
          performHapticClick();
          viewModel.setOverviewFabInfoShown();
          activity.navUtil.navigate(
              R.id.purchaseFragment,
              new PurchaseFragmentArgs.Builder()
                  .setStartWithScanner(true).build().toBundle()
          );
        }).setOnCancelListener(dialog -> performHapticClick()).create();
    dialogFabInfo.show();
    return true;
  }

  // startLogoAnimation() entfaellt: die Startseite hat laut Vorlage kein Logo mehr.

  @Override
  public void updateConnectivity(boolean online) {
    if (!online == viewModel.isOffline()) {
      return;
    }
    viewModel.downloadData(false);
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}
