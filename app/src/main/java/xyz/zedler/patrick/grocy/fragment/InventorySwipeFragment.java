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
 * Copyright (c) 2026 by Fabian Ariu (Scanory fork: swipe inventory)
 */

package xyz.zedler.patrick.grocy.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentInventorySwipeBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.model.StockItem;
import xyz.zedler.patrick.grocy.util.NumUtil;
import xyz.zedler.patrick.grocy.util.ResUtil;
import xyz.zedler.patrick.grocy.viewmodel.InventorySwipeViewModel;

/** Schnelle Inventur: Karte wischen (rechts stimmt, links weg) oder Menge per Stepper korrigieren. */
public class InventorySwipeFragment extends BaseFragment {

  private MainActivity activity;
  private FragmentInventorySwipeBinding binding;
  private InventorySwipeViewModel viewModel;
  private double count = 0;

  @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) {
    binding = FragmentInventorySwipeBinding.inflate(i, c, false); return binding.getRoot();
  }
  @Override public void onDestroyView() { super.onDestroyView(); binding = null; }

  @SuppressLint("ClickableViewAccessibility")
  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(InventorySwipeViewModel.class);
    SystemBarBehavior sbb = new SystemBarBehavior(activity);
    sbb.setAppBar(binding.appBar); sbb.setContainer(binding.container);
    sbb.applyAppBarInsetOnContainer(false); sbb.applyStatusBarInsetOnContainer(false); sbb.setUp();
    activity.setSystemBarBehavior(sbb);
    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());

    binding.buttonMinus.setOnClickListener(v -> { if (count > 0) count = Math.max(0, count - 1); renderCount(); });
    binding.buttonPlus.setOnClickListener(v -> { count += 1; renderCount(); });
    binding.buttonOk.setOnClickListener(v -> animateOut(1));
    binding.buttonGone.setOnClickListener(v -> { count = 0; animateOut(-1); });
    binding.buttonUndo.setOnClickListener(v -> viewModel.undo());

    // Tinder-Geste: horizontal ziehen, ab einem Drittel der Breite entscheiden
    binding.card.setOnTouchListener(new View.OnTouchListener() {
      float downX, downY; boolean dragging;
      @Override public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN: downX = e.getRawX(); downY = e.getRawY(); dragging = false; return true;
          case MotionEvent.ACTION_MOVE: {
            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
            if (!dragging && Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy)) dragging = true;
            if (dragging) { v.setTranslationX(dx); v.setRotation(dx / 40f); float f = Math.min(1f, Math.abs(dx) / (v.getWidth() / 3f));
              binding.labelOk.setAlpha(dx > 0 ? f : 0); binding.labelGone.setAlpha(dx < 0 ? f : 0); }
            return true; }
          case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
            float dx = v.getTranslationX();
            if (dragging && Math.abs(dx) > v.getWidth() / 3f) { if (dx < 0) count = 0; animateOut(dx > 0 ? 1 : -1); }
            else { v.animate().translationX(0).rotation(0).setDuration(180).start(); binding.labelOk.animate().alpha(0).start(); binding.labelGone.animate().alpha(0).start(); }
            return true; }
        }
        return false;
      }
    });

    viewModel.getCurrentLive().observe(getViewLifecycleOwner(), this::renderItem);
    viewModel.getProgressLive().observe(getViewLifecycleOwner(), p -> {
      if (binding == null) return;
      binding.textProgress.setText(p);
      int total = viewModel.getTotal(); int done = viewModel.getDone();
      binding.progressFill.post(() -> { if (binding == null) return; ViewGroup parent = (ViewGroup) binding.progressFill.getParent();
        binding.progressFill.getLayoutParams().width = total == 0 ? 0 : parent.getWidth() * done / total; binding.progressFill.requestLayout(); });
      binding.buttonUndo.setVisibility(viewModel.canUndo() ? View.VISIBLE : View.INVISIBLE);
    });
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
    });
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
    viewModel.loadFromDatabase();
  }

  private void animateOut(int dir) {
    if (binding == null) return;
    final double decided = count;
    binding.card.animate().translationX(dir * binding.card.getWidth() * 1.3f).rotation(dir * 18).setDuration(220).withEndAction(() -> {
      if (binding == null) return;
      binding.card.setTranslationX(0); binding.card.setRotation(0); binding.labelOk.setAlpha(0); binding.labelGone.setAlpha(0);
      viewModel.decide(decided);
    }).start();
  }

  private void renderItem(StockItem item) {
    if (binding == null) return;
    boolean finished = item == null;
    binding.card.setVisibility(finished ? View.GONE : View.VISIBLE);
    binding.buttonOk.setVisibility(finished ? View.GONE : View.VISIBLE);
    binding.buttonGone.setVisibility(finished ? View.GONE : View.VISIBLE);
    binding.textDone.setVisibility(finished && viewModel.isFinished() ? View.VISIBLE : View.GONE);
    if (finished) return;
    binding.textName.setText(viewModel.productName(item));
    binding.textAmount.setText(getString(R.string.inv_according_to_grocy, viewModel.amountText(item)));
    count = viewModel.currentAmount();
    renderCount();
  }

  private void renderCount() {
    if (binding == null) return;
    binding.textCount.setText(NumUtil.trimAmount(count, 2));
    boolean same = Math.abs(count - viewModel.currentAmount()) < 0.0001;
    binding.textState.setText(same ? R.string.inv_state_ok : R.string.inv_state_corrected);
    binding.textState.setTextColor(ResUtil.getColor(activity, same ? R.attr.colorSecondary : R.attr.colorTertiary));
    binding.buttonOk.setText(same ? R.string.inv_ok : R.string.inv_ok_corrected);
  }
}
