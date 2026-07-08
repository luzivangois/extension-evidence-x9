package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.RequirementFilterOption;
import com.conviso.x9.model.RequirementItem;
import com.conviso.x9.model.X9Item;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private JTextField projectFilterField;
    private JComboBox<RequirementFilterOption> requirementFilter;
    private JComboBox<String> stateFilter;
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
        syncRequirementFilterOptions();

        String projectFilter = safeLower(projectFilterField.getText().trim());
        RequirementFilterOption selectedRequirement = (RequirementFilterOption) requirementFilter.getSelectedItem();
        String requirementFilterId = selectedRequirement == null ? "ALL" : safe(selectedRequirement.getId());
        String stateFilterValue = String.valueOf(stateFilter.getSelectedItem());

        pendingItems.clear();
        sentModel.clear();

        for (int i = 0; i < model.size(); i++) {
            X9Item item = model.get(i);

            boolean projectMatch = projectFilter.isEmpty()
                || safeLower(item.getProjectId()).contains(projectFilter)
                || safeLower(item.getTitle()).contains(projectFilter);
            if (!projectMatch) {
                continue;
            }
            if (!"ALL".equals(stateFilterValue) && !safe(item.getState()).equals(stateFilterValue)) {
                continue;
            }

            if ("SENT".equals(item.getState())) {
                sentModel.addElement(item);
            } else {
                if (!"ALL".equals(requirementFilterId) && !safe(item.getRequirementId()).equals(requirementFilterId)) {
                    continue;
                }
                pendingItems.add(item);
            }
        }
        pendingTableModel.fireTableDataChanged();
    }

    private void syncRequirementFilterOptions() {
        RequirementFilterOption selected = (RequirementFilterOption) requirementFilter.getSelectedItem();
        String selectedId = selected == null ? "ALL" : safe(selected.getId());

        requirementFilter.removeAllItems();
        requirementFilter.addItem(new RequirementFilterOption("ALL", "Todos"));

        List<String> seenIds = new ArrayList<>();

        for (RequirementItem req : extension.requirementCatalog()) {
            if (req == null || safe(req.getId()).isEmpty() || seenIds.contains(req.getId())) {
                continue;
            }
            requirementFilter.addItem(new RequirementFilterOption(req.getId(), req.toString()));
            seenIds.add(req.getId());
        }

        for (int i = 0; i < model.size(); i++) {
            X9Item item = model.get(i);
            String reqId = safe(item.getRequirementId());
            if (reqId.isEmpty() || seenIds.contains(reqId)) {
                continue;
            }
            String label = "Req " + reqId + " - " + (safe(item.getTitle()).isEmpty() ? "(sem titulo)" : safe(item.getTitle()));
            requirementFilter.addItem(new RequirementFilterOption(reqId, label));
            seenIds.add(reqId);
        }

        for (int i = 0; i < requirementFilter.getItemCount(); i++) {
            RequirementFilterOption option = requirementFilter.getItemAt(i);
            if (option != null && safe(option.getId()).equals(selectedId)) {
                requirementFilter.setSelectedIndex(i);
                return;
            }
        }
        requirementFilter.setSelectedIndex(0);
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
        JButton saveButton = new JButton("Salvar edicao");
        JButton refreshAiButton = new JButton("Refresh IA");
        JButton sendButton = new JButton("Enviar selecionado");
        JButton sendAllButton = new JButton("Enviar pendentes");
        JButton deleteButton = new JButton("Excluir selecionado");
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
        projectFilterField = new JTextField(18);
        requirementFilter = new JComboBox<>();
        stateFilter = new JComboBox<>(new String[]{"ALL", "DRAFT", "SENT"});
        JButton applyFilterButton = new JButton("Aplicar filtro");

        filters.add(new JLabel("Projeto:"));
        filters.add(projectFilterField);
        filters.add(new JLabel("Requirement (Pendentes):"));
        filters.add(requirementFilter);
        filters.add(new JLabel("Estado:"));
        filters.add(stateFilter);
        filters.add(applyFilterButton);

        requirementFilter.addItem(new RequirementFilterOption("ALL", "Todos"));
        requirementFilter.setSelectedIndex(0);

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
        applyFilterButton.addActionListener(e -> refreshViews());

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

    private static String safeLower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }
}
