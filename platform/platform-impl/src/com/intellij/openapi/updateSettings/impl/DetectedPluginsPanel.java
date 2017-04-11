/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginHeaderPanel;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 * Date: 04-Dec-2007
 */
public class DetectedPluginsPanel extends OrderPanel<PluginDownloader> {
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private JEditorPane myDescriptionPanel = new JEditorPane();
  private PluginHeaderPanel myHeader;

  public DetectedPluginsPanel() {
    super(PluginDownloader.class);
    JTable entryTable = getEntryTable();
    myHeader = new PluginHeaderPanel(null);
    entryTable.setTableHeader(null);
    entryTable.setDefaultRenderer(PluginDownloader.class, new ColoredTableCellRenderer() {
      protected void customizeCellRenderer(JTable table,
                                           Object value,
                                           boolean selected,
                                           boolean hasFocus,
                                           int row,
                                           int column) {
        setBorder(null);
        PluginDownloader downloader = (PluginDownloader)value;
        if (downloader != null) {
          String pluginName = downloader.getPluginName();
          append(pluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(PluginId.getId(downloader.getPluginId()));
          if (ideaPluginDescriptor != null) {
            String oldPluginName = ideaPluginDescriptor.getName();
            if (!Comparing.strEqual(pluginName, oldPluginName)) {
              append(" - " + oldPluginName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
          String loadedVersion = downloader.getPluginVersion();
          if (loadedVersion != null || (ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null)) {
            String installedVersion = ideaPluginDescriptor != null && ideaPluginDescriptor.getVersion() != null
                                            ? ideaPluginDescriptor.getVersion() + (loadedVersion != null ? " " + UIUtil.rightArrow() + " " : "")
                                            : "";
            String availableVersion = loadedVersion != null ? loadedVersion : "";
            append(" " + installedVersion + availableVersion, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
          }
        }
      }
    });
    entryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        int selectedRow = entryTable.getSelectedRow();
        if (selectedRow != -1) {
          PluginDownloader selection = getValueAt(selectedRow);
          IdeaPluginDescriptor descriptor = selection.getDescriptor();
          if (descriptor != null) {
            PluginManagerMain.pluginInfoUpdate(descriptor, null, myDescriptionPanel, myHeader);
          }
        }
      }
    });
    setCheckboxColumnName("");
    myDescriptionPanel.setPreferredSize(new Dimension(400, -1));
    myDescriptionPanel.setEditable(false);
    myDescriptionPanel.setContentType(UIUtil.HTML_MIME);
    myDescriptionPanel.addHyperlinkListener(new PluginManagerMain.MyHyperlinkListener());
    removeAll();

    Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(entryTable));
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myDescriptionPanel));
    add(splitter, BorderLayout.CENTER);
  }

  public String getCheckboxColumnName() {
    return "";
  }

  public boolean isCheckable(PluginDownloader downloader) {
    return true;
  }

  public boolean isChecked(PluginDownloader downloader) {
    return !getSkippedPlugins().contains(downloader.getPluginId());
  }

  public void setChecked(PluginDownloader downloader, boolean checked) {
    if (checked) {
      getSkippedPlugins().remove(downloader.getPluginId());
    }
    else {
      getSkippedPlugins().add(downloader.getPluginId());
    }
    for (Listener listener : myListeners) {
      listener.stateChanged();
    }
  }

  protected Set<String> getSkippedPlugins() {
    return UpdateChecker.getDisabledToUpdatePlugins();
  }

  public void addStateListener(Listener l) {
    myListeners.add(l);
  }

  public interface Listener {
    void stateChanged();
  }
}