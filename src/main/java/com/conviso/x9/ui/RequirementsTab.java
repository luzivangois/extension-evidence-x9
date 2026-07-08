package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.RequirementItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/** Builds and owns the "Requirements" tab: the project's requirement catalog plus a run log. */
public final class RequirementsTab {

    private final ConvisoExtension extension;
    private final JPanel panel;

    private DefaultListModel<RequirementItem> model;
    private JList<RequirementItem> list;
    private JTextArea outputArea;
    private boolean suppressSelectionEvent;

    public RequirementsTab(ConvisoExtension extension) {
        this.extension = extension;
        this.panel = build();
    }

    public JPanel getPanel() {
        return panel;
    }

    public DefaultListModel<RequirementItem> getModel() {
        return model;
    }

    public RequirementItem selectedRequirement() {
        return list.getSelectedValue();
    }

    public void selectRequirementById(String requirementId) {
        for (int i = 0; i < model.size(); i++) {
            RequirementItem item = model.get(i);
            if (item != null && item.getId().equals(requirementId)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    public void fillRequirements(JsonArray requirements) {
        String selectedRequirementId = extension.currentRequirementId();
        suppressSelectionEvent = true;
        model.clear();

        for (int i = 0; i < requirements.size(); i++) {
            JsonObject obj = requirements.get(i).getAsJsonObject();
            model.addElement(new RequirementItem(
                getString(obj, "id"),
                getString(obj, "status"),
                getString(obj, "title"),
                getString(obj, "description")
            ));
        }

        if (!selectedRequirementId.isEmpty()) {
            selectRequirementById(selectedRequirementId);
        } else if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        suppressSelectionEvent = false;
        extension.refreshX9Views();
    }

    public void appendOutput(String text) {
        if (outputArea == null) {
            extension.getLogger().info(text);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private JPanel build() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton loadRequirementsButton = new JButton("Load requirements");
        JButton clearButton = new JButton("Clear output");
        toolbar.add(loadRequirementsButton);
        toolbar.add(clearButton);
        extension.registerBusyButton(loadRequirementsButton);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressSelectionEvent) {
                return;
            }
            RequirementItem selected = selectedRequirement();
            if (selected != null) {
                extension.stageInX9(null, selected.getId());
            }
        });

        outputArea = new JTextArea();
        outputArea.setEditable(false);

        String savedRequirementId = extension.getSettings().getRequirementId();
        if (!savedRequirementId.isEmpty()) {
            suppressSelectionEvent = true;
            model.addElement(new RequirementItem(savedRequirementId, "saved", "Requirement salvo", ""));
            list.setSelectedIndex(0);
            suppressSelectionEvent = false;
        }

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(list),
            new JScrollPane(outputArea)
        );
        split.setResizeWeight(0.42);

        loadRequirementsButton.addActionListener(e -> extension.loadRequirements());
        clearButton.addActionListener(e -> outputArea.setText(""));

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }
}
