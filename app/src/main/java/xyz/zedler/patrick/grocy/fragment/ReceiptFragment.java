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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.ReceiptLineAdapter;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentReceiptBinding;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.viewmodel.ReceiptViewModel;
import xyz.zedler.patrick.grocy.viewmodel.ReceiptViewModel.Line;

/** Kassenzettel (Vorlage 07): fotografieren, Positionen pruefen/zuordnen, einlagern. */
public class ReceiptFragment extends BaseFragment implements ReceiptLineAdapter.Listener {

  private MainActivity activity;
  private FragmentReceiptBinding binding;
  private ReceiptViewModel viewModel;
  private ReceiptLineAdapter adapter;
  private ActivityResultLauncher<Intent> takePicture;

  @Override public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    takePicture = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
      if (result.getResultCode() == Activity.RESULT_OK) viewModel.recognize(viewModel.getCurrentFilePath());
    });
  }

  @Override public View onCreateView(@NonNull LayoutInflater i, ViewGroup c, Bundle s) { binding = FragmentReceiptBinding.inflate(i, c, false); return binding.getRoot(); }
  @Override public void onDestroyView() { super.onDestroyView(); if (binding != null) { binding.recycler.setAdapter(null); binding = null; } }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    viewModel = new ViewModelProvider(this).get(ReceiptViewModel.class);
    SystemBarBehavior sbb = new SystemBarBehavior(activity);
    sbb.setAppBar(binding.appBar); sbb.setContainer(binding.container); sbb.setRecycler(binding.recycler);
    sbb.applyAppBarInsetOnContainer(false); sbb.applyStatusBarInsetOnContainer(false); sbb.setUp();
    activity.setSystemBarBehavior(sbb);
    binding.toolbar.setNavigationOnClickListener(v -> activity.navUtil.navigateUp());
    binding.toolbar.setOnMenuItemClickListener(item -> { if (item.getItemId() == R.id.action_camera) { capture(); return true; } return false; });
    binding.empty.setOnClickListener(v -> capture());
    binding.recycler.setLayoutManager(new LinearLayoutManager(activity));
    binding.recycler.setBackgroundResource(R.drawable.glass_card);
    adapter = new ReceiptLineAdapter(this);
    binding.recycler.setAdapter(adapter);
    binding.buttonAdd.setOnClickListener(v -> viewModel.purchaseAll());
    viewModel.getLinesLive().observe(getViewLifecycleOwner(), lines -> {
      adapter.updateData(lines);
      boolean has = lines != null && !lines.isEmpty();
      binding.recycler.setVisibility(has ? View.VISIBLE : View.GONE);
      binding.empty.setVisibility(has ? View.GONE : View.VISIBLE);
      binding.bottom.setVisibility(has ? View.VISIBLE : View.GONE);   // leer: kein 0-Positionen-Knopf
      int n = viewModel.checkedCount();
      binding.buttonAdd.setEnabled(n > 0); binding.buttonAdd.setAlpha(n > 0 ? 1f : 0.5f);
      binding.buttonAdd.setText(getResources().getQuantityString(R.plurals.receipt_button_n, n, n));
    });
    viewModel.getSubtitleLive().observe(getViewLifecycleOwner(), s -> { binding.textSubtitle.setText(s); binding.textSubtitle.setVisibility(s == null ? View.GONE : View.VISIBLE); });
    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(), l -> binding.progress.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.GONE));
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) activity.showSnackbar(((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain));
    });
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.recycler, true, true);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(false, R.menu.menu_empty);
    viewModel.loadFromDatabase();
    // Bild von aussen (Deep-Link mit Pfad, z. B. geteiltes Foto): direkt erkennen
    String path = getArguments() == null ? null : getArguments().getString("path");
    if (path != null && savedInstanceState == null) binding.getRoot().postDelayed(() -> viewModel.recognize(path), 600);
  }

  private void capture() {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    File file;
    try { file = viewModel.createImageFile(); } catch (IOException e) { viewModel.showErrorMessage(); return; }
    Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
    intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if (intent.resolveActivity(requireContext().getPackageManager()) == null) { viewModel.showMessage(getString(R.string.receipt_no_camera)); return; }
    takePicture.launch(intent);
  }

  @Override public void onToggle(Line line) { viewModel.toggle(line); }

  @Override public void onAssign(Line line) {
    List<Product> products = new ArrayList<>(viewModel.getProducts());
    products.sort((a, b) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
    String[] names = new String[products.size()];
    for (int i = 0; i < products.size(); i++) names[i] = products.get(i).getName();
    new MaterialAlertDialogBuilder(activity).setTitle(line.raw).setItems(names, (d, w) -> viewModel.assign(line, products.get(w))).show();
  }
}
