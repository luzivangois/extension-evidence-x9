package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.VulnerabilityRecord;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Builds and owns the "Vulnerabilities" tab: the project's known findings, loaded from the platform. */
public final class VulnerabilitiesTab {

    private final ConvisoExtension extension;
    private final JPanel panel;

    private DefaultListModel<VulnerabilityRecord> model;
    private JList<VulnerabilityRecord> list;
    private JLabel statusLabel;
    private JButton includeButton;

    public VulnerabilitiesTab(ConvisoExtension extension) {
        this.extension = extension;
        this.panel = build();
    }

    public JComponent getPanel() {
        return panel;
    }

    public DefaultListModel<VulnerabilityRecord> getModel() {
        return model;
    }

    public VulnerabilityRecord getSelectedRecord() {
        return list.getSelectedValue();
    }

    public List<VulnerabilityRecord> allItems() {
        List<VulnerabilityRecord> items = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            items.add(model.get(i));
        }
        return items;
    }

    public void clearModel() {
        model.clear();
    }

    public void addOrMergeRecord(VulnerabilityRecord record) {
        if (record == null) {
            return;
        }
        String key = keyOf(record);
        for (int i = 0; i < model.size(); i++) {
            if (key.equals(keyOf(model.get(i)))) {
                model.remove(i);
                model.add(i, record);
                return;
            }
        }
        model.addElement(record);
    }

    public void selectFirst() {
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
            list.ensureIndexIsVisible(0);
        }
    }

    public void refreshStatus() {
        statusLabel.setText("Total de vulnerabilidades carregadas: " + model.size());
        if (includeButton != null) {
            includeButton.setEnabled(list.getSelectedValue() != null);
        }
    }

    private JPanel build() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Vulnerabilities");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));

        statusLabel = new JLabel("Total de vulnerabilidades carregadas: 0");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(statusLabel, BorderLayout.EAST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton loadButton = new JButton("Load project vulnerabilities");
        includeButton = new JButton("Follow requeriment");
        includeButton.setEnabled(false);
        toolbar.add(loadButton);
        toolbar.add(includeButton);
        extension.registerBusyButton(loadButton);
        extension.registerBusyButton(includeButton);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createTitledBorder("Vulnerabilidades do projeto"));

        loadButton.addActionListener(e -> extension.loadProjectVulnerabilities());
        includeButton.addActionListener(e -> extension.includeSelectedVulnerabilityInRequirements());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                includeButton.setEnabled(list.getSelectedValue() != null);
            }
        });

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.add(toolbar, BorderLayout.NORTH);
        body.add(listScroll, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static String keyOf(VulnerabilityRecord record) {
        if (record == null) {
            return "";
        }
        if (!safe(record.getId()).isEmpty()) {
            return safe(record.getId());
        }
        return safe(record.getProjectId()) + "|" + safe(record.getTitle()) + "|" + safe(record.getTemplate())
            + "|" + safe(record.getEndpoint()) + "|" + safe(record.getMethod()) + "|" + safe(record.getSeverity());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
