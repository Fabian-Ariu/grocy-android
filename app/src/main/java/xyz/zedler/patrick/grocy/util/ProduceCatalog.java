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

package xyz.zedler.patrick.grocy.util;

/** Eingebauter Obst-&-Gemuese-Katalog (Vorlage 04): Name, Kategorie, Standard-Haltbarkeit in Tagen. */
public final class ProduceCatalog {

  public static final String[] CATEGORIES = {
      "Kernobst", "Steinobst", "Beeren & Trauben", "Südfrüchte", "Fruchtgemüse",
      "Wurzeln & Knollen", "Zwiebeln & Lauch", "Kohl", "Salat & Kräuter", "Pilze"
  };

  /** Standardauswahl fuer "Haeufig", solange noch keine Gewohnheit gelernt ist. */
  public static final String[] DEFAULT_FREQUENT = {"Äpfel", "Bananen", "Karotten", "Tomaten", "Kartoffeln", "Gurke"};

  public static final class Entry {
    public final String name; public final int category; public final int days;
    Entry(String name, int category, int days) { this.name = name; this.category = category; this.days = days; }
  }

  private static Entry e(String n, int c, int d) { return new Entry(n, c, d); }

  public static final Entry[] ENTRIES = {
      e("Äpfel", 0, 14), e("Birnen", 0, 7), e("Quitten", 0, 21),
      e("Zwetschgen", 1, 5), e("Pflaumen", 1, 5), e("Kirschen", 1, 3), e("Aprikosen", 1, 4), e("Pfirsiche", 1, 4), e("Nektarinen", 1, 4),
      e("Erdbeeren", 2, 2), e("Himbeeren", 2, 2), e("Heidelbeeren", 2, 5), e("Brombeeren", 2, 2), e("Johannisbeeren", 2, 3), e("Trauben", 2, 7),
      e("Bananen", 3, 5), e("Orangen", 3, 14), e("Mandarinen", 3, 10), e("Zitronen", 3, 21), e("Limetten", 3, 14), e("Grapefruit", 3, 14),
      e("Kiwi", 3, 10), e("Ananas", 3, 5), e("Mango", 3, 5), e("Avocado", 3, 4), e("Melone", 3, 5), e("Granatapfel", 3, 14),
      e("Tomaten", 4, 7), e("Cherrytomaten", 4, 7), e("Gurke", 4, 7), e("Peperoni", 4, 10), e("Zucchini", 4, 7), e("Aubergine", 4, 7), e("Kürbis", 4, 30), e("Chili", 4, 14),
      e("Karotten", 5, 21), e("Kartoffeln", 5, 30), e("Süsskartoffeln", 5, 21), e("Randen", 5, 21), e("Sellerie", 5, 14), e("Rettich", 5, 7),
      e("Radieschen", 5, 5), e("Ingwer", 5, 21), e("Pastinaken", 5, 14), e("Fenchel", 5, 7),
      e("Zwiebeln", 6, 30), e("Knoblauch", 6, 30), e("Lauch", 6, 10), e("Frühlingszwiebeln", 6, 7), e("Schalotten", 6, 30),
      e("Brokkoli", 7, 5), e("Blumenkohl", 7, 7), e("Weisskohl", 7, 21), e("Rotkohl", 7, 21), e("Rosenkohl", 7, 7), e("Wirsing", 7, 10), e("Kohlrabi", 7, 10), e("Chinakohl", 7, 10),
      e("Kopfsalat", 8, 3), e("Eisbergsalat", 8, 7), e("Rucola", 8, 3), e("Nüsslisalat", 8, 3), e("Spinat", 8, 3), e("Petersilie", 8, 5),
      e("Basilikum", 8, 4), e("Schnittlauch", 8, 5), e("Dill", 8, 4), e("Koriander", 8, 4),
      e("Champignons", 9, 4), e("Steinpilze", 9, 3)
  };

  public static String key(String name) {
    return name == null ? "" : name.trim().toLowerCase().replace("ß", "ss");
  }

  private ProduceCatalog() {}
}
