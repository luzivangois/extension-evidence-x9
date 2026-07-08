package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.RequirementItem;
import com.conviso.x9.model.X9Item;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds and owns the "X9" tab: the draft/sent evidence-summary queue for the selected project. */
public final class X9Tab {

    private final ConvisoExtension extension;
    private final JPanel panel;

    private DefaultListModel<X9Item> model;
    private final List<X9Item> pendingItems = new ArrayList<>();
    private PendingTableModel pendingTableModel;
    private DefaultListModel<X9Item> sentModel;
    private JTable pendingTable;
    private JList<X9Item> sentList;
    private JLabel projectDisplayField;
    private JLabel requirementStatusSummaryLabel;
    private JTextArea summaryArea;
    private JLabel statusLabel;
    private X9Item selectedItem;
    private boolean suppressSelectionEvent;

    public X9Tab(ConvisoExtension extension) {
        this.extension = extension;
        this.panel = build();
    }

    public JPanel getPanel() {
        return panel;
    }

    public DefaultListModel<X9Item> getModel() {
        return model;
    }

    public List<X9Item> allItems() {
        List<X9Item> items = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            items.add(model.get(i));
        }
        return items;
    }

    public X9Item getSelectedItem() {
        return selectedItem;
    }

    public String getSummaryText() {
        return summaryArea.getText().trim();
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public X9Item findByKey(String key) {
        for (int i = 0; i < model.size(); i++) {
            X9Item item = model.get(i);
            if (key.equals(keyOf(item))) {
                return item;
            }
        }
        return null;
    }

    public void addItem(X9Item item) {
        model.addElement(item);
        refreshViews();
        selectItemByKey(keyOf(item));
    }

    public void notifyItemChanged(X9Item updated) {
        String key = keyOf(updated);
        for (int i = 0; i < model.size(); i++) {
            if (key.equals(keyOf(model.get(i)))) {
                model.set(i, updated);
                refreshViews();
                selectItemByKey(key);
                return;
            }
        }
    }

    public void loadItemsSilently(List<X9Item> items) {
        for (X9Item item : items) {
            if (item != null) {
                model.addElement(item);
            }
        }
        refreshViews();
    }

    public void removeItemByKey(String key) {
        for (int i = 0; i < model.size(); i++) {
            if (key.equals(keyOf(model.get(i)))) {
                model.remove(i);
                break;
            }
        }
        pendingTable.clearSelection();
        sentList.clearSelection();
        updateSelection(null);
        refreshViews();
    }

    public void selectItemByKey(String key) {
        for (int i = 0; i < pendingItems.size(); i++) {
            X9Item item = pendingItems.get(i);
            if (key.equals(keyOf(item))) {
                suppressSelectionEvent = true;
                try {
                    pendingTable.getSelectionModel().setSelectionInterval(i, i);
                } finally {
                    suppressSelectionEvent = false;
                }
                pendingTable.scrollRectToVisible(pendingTable.getCellRect(i, 0, true));
                updateSelection(item);
                return;
            }
        }
        for (int i = 0; i < sentModel.size(); i++) {
            X9Item item = sentModel.get(i);
            if (key.equals(keyOf(item))) {
                sentList.setSelectedIndex(i);
                sentList.ensureIndexIsVisible(i);
                updateSelection(item);
                return;
            }
        }
        updateSelection(null);
    }

    public void refreshViews() {
        projectDisplayField.setText(extension.currentProjectDisplay());
        requirementStatusSummaryLabel.setText(buildRequirementStatusSummary());

        String currentProjectId = extension.currentProjectId();
        pendingItems.clear();
        sentModel.clear();

        for (int i = 0; i < model.size(); i++) {
            X9Item item = model.get(i);
            if (!safe(item.getProjectId()).equals(currentProjectId)) {
                continue;
            }
            if ("SENT".equals(item.getState())) {
                sentModel.addElement(item);
            } else {
                pendingItems.add(item);
            }
        }
        pendingTableModel.fireTableDataChanged();
    }

    private String buildRequirementStatusSummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("NOT_STARTED", 0);
        counts.put("IN_PROGRESS", 0);
        counts.put("NOT_APPLICABLE", 0);
        counts.put("DONE", 0);
        counts.put("NOT_ACCORDING", 0);

        int total = 0;
        for (RequirementItem req : extension.requirementCatalog()) {
            if (req == null) {
                continue;
            }
            total++;
            String status = safe(req.getStatus()).toUpperCase(Locale.ROOT);
            if (counts.containsKey(status)) {
                counts.merge(status, 1, Integer::sum);
            }
        }

        StringBuilder summary = new StringBuilder("Requirements (").append(total).append("): ");
        summary.append("To Do ").append(counts.getOrDefault("NOT_STARTED", 0)).append(" | ");
        summary.append("Running ").append(counts.getOrDefault("IN_PROGRESS", 0)).append(" | ");
        summary.append("Not Applicable ").append(counts.getOrDefault("NOT_APPLICABLE", 0)).append(" | ");
        summary.append("Done ").append(counts.getOrDefault("DONE", 0)).append(" | ");
        summary.append("Not According ").append(counts.getOrDefault("NOT_ACCORDING", 0));
        return summary.toString();
    }

    private void updateSelection(X9Item item) {
        selectedItem = item;
        if (item == null) {
            summaryArea.setText("");
            statusLabel.setText("Nenhum item selecionado.");
            return;
        }
        summaryArea.setText(safe(item.getSummary()));
        String stamp = safe(item.getSentAt()).isEmpty() ? "-" : item.getSentAt();
        String approver = safe(item.getApprovedBy()).isEmpty() ? "-" : item.getApprovedBy();
        statusLabel.setText("Projeto: " + item.getProjectId() + " | Requirement: " + item.getRequirementId()
            + " | Estado: " + item.getState() + " | SentAt: " + stamp + " | Aprovador: " + approver);
    }

    private JPanel build() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton saveButton = new JButton("Salvar Edição");
        JButton refreshAiButton = new JButton("Atualizar Análise de I.A");
        JButton sendButton = new JButton("Enviar Selecionado");
        JButton sendAllButton = new JButton("Enviar Todos");
        JButton deleteButton = new JButton("Excluir Selecionado");
        toolbar.add(saveButton);
        toolbar.add(refreshAiButton);
        toolbar.add(sendButton);
        toolbar.add(sendAllButton);
        toolbar.add(deleteButton);
        extension.registerBusyButton(saveButton);
        extension.registerBusyButton(refreshAiButton);
        extension.registerBusyButton(sendButton);
        extension.registerBusyButton(sendAllButton);
        extension.registerBusyButton(deleteButton);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        projectDisplayField = new JLabel();
        projectDisplayField.setOpaque(true);
        projectDisplayField.setBackground(new Color(238, 238, 238));
        projectDisplayField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        requirementStatusSummaryLabel = new JLabel();

        filters.add(new JLabel("Projeto:"));
        filters.add(projectDisplayField);
        filters.add(requirementStatusSummaryLabel);

        model = new DefaultListModel<>();
        sentModel = new DefaultListModel<>();

        pendingTableModel = new PendingTableModel();
        pendingTable = new JTable(pendingTableModel);
        pendingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendingTable.setRowSelectionAllowed(true);
        pendingTable.getColumnModel().getColumn(0).setMaxWidth(60);
        pendingTable.getColumnModel().getColumn(0).setMinWidth(60);

        sentList = new JList<>(sentModel);
        sentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sentList.setCellRenderer(buildSentRenderer());

        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(BorderFactory.createTitledBorder("Pendentes"));
        JScrollPane sentScroll = new JScrollPane(sentList);
        sentScroll.setBorder(BorderFactory.createTitledBorder("Enviados"));

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pendingScroll, sentScroll);
        leftSplit.setResizeWeight(0.55);

        summaryArea = new JTextArea();
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);

        statusLabel = new JLabel("Nenhum item selecionado.");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, new JScrollPane(summaryArea));
        split.setResizeWeight(0.40);

        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressSelectionEvent) {
                return;
            }
            int row = pendingTable.getSelectedRow();
            if (row >= 0 && row < pendingItems.size()) {
                sentList.clearSelection();
                updateSelection(pendingItems.get(row));
                return;
            }
            if (sentList.getSelectedValue() == null) {
                updateSelection(null);
            }
        });

        sentList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            X9Item item = sentList.getSelectedValue();
            if (item != null) {
                pendingTable.clearSelection();
                updateSelection(item);
                return;
            }
            if (pendingTable.getSelectedRow() < 0) {
                updateSelection(null);
            }
        });

        saveButton.addActionListener(e -> extension.saveSelectedX9Summary());
        refreshAiButton.addActionListener(e -> extension.refreshSelectedX9WithAi());
        sendButton.addActionListener(e -> extension.sendSelectedX9());
        sendAllButton.addActionListener(e -> extension.sendAllPendingX9());
        deleteButton.addActionListener(e -> extension.deleteSelectedX9Item());

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(toolbar, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);

        refreshViews();
        return panel;
    }

    private DefaultListCellRenderer buildSentRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof X9Item && !isSelected) {
                    X9Item item = (X9Item) value;
                    setForeground("SENT".equals(item.getState()) ? new Color(0, 128, 0) : new Color(50, 50, 50));
                }
                return component;
            }
        };
    }

    /** Backs the "Pendentes" table: column 0 is the "Done" checkbox, column 1 is the item's display text. */
    private final class PendingTableModel extends AbstractTableModel {

        private final String[] columnNames = {"Done", "Requirement"};

        @Override
        public int getRowCount() {
            return pendingItems.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            X9Item item = pendingItems.get(rowIndex);
            return columnIndex == 0 ? item.isMarkAsDone() : item.toString();
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            pendingItems.get(rowIndex).setMarkAsDone(Boolean.TRUE.equals(value));
            extension.persistX9Items();
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    public static String keyOf(X9Item item) {
        if (item == null) {
            return "";
        }
        if (!safe(item.getEntryId()).isEmpty()) {
            return safe(item.getEntryId());
        }
        return keyOf(item.getProjectId(), item.getRequirementId());
    }

    public static String keyOf(String projectId, String requirementId) {
        return safe(projectId) + "::" + safe(requirementId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
