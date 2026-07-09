package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.VulnerabilityRecord;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Builds and owns the "Vulnerabilities" tab: the project's known findings, loaded from the platform. */
public final class VulnerabilitiesTab {

    private final ConvisoExtension extension;
    private final JPanel panel;

    private DefaultListModel<VulnerabilityRecord> model;
    private JList<VulnerabilityRecord> list;
    private JLabel projectDisplayField;
    private JLabel vulnerabilityStatusSummaryLabel;

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
        projectDisplayField.setText(extension.currentProjectDisplay());
        vulnerabilityStatusSummaryLabel.setText(buildVulnerabilityStatusSummary());
    }

    private String buildVulnerabilityStatusSummary() {
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < model.size(); i++) {
            String status = safe(model.get(i).getStatus()).trim().toUpperCase(Locale.ROOT);
            counts.merge(status.isEmpty() ? "UNKNOWN" : status, 1, Integer::sum);
        }

        StringBuilder summary = new StringBuilder("Vulnerabilities (").append(model.size()).append(")");
        if (!counts.isEmpty()) {
            summary.append(": ");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (!first) {
                    summary.append(" | ");
                }
                summary.append(entry.getKey()).append(" ").append(entry.getValue());
                first = false;
            }
        }
        return summary.toString();
    }

    private JPanel build() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton loadButton = new JButton("Load Project Vulnerabilities");
        toolbar.add(loadButton);
        extension.registerBusyButton(loadButton);

        projectDisplayField = new JLabel();
        projectDisplayField.setOpaque(true);
        projectDisplayField.setBackground(new Color(238, 238, 238));
        projectDisplayField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        vulnerabilityStatusSummaryLabel = new JLabel();

        toolbar.add(new JLabel("Project:"));
        toolbar.add(projectDisplayField);
        toolbar.add(vulnerabilityStatusSummaryLabel);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createTitledBorder("Project Vulnerabilities"));

        loadButton.addActionListener(e -> extension.loadProjectVulnerabilities());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = list.locationToIndex(e.getPoint());
                if (row < 0) {
                    return;
                }
                list.setSelectedIndex(row);
                VulnerabilityRecord record = model.get(row);

                JPopupMenu popup = new JPopupMenu();
                JMenu requirementsMenu = extension.buildRequirementsMenu(
                    requirement -> extension.stageVulnerabilityInX9(record, requirement.getId())
                );
                popup.add(requirementsMenu);
                popup.show(list, e.getX(), e.getY());
            }
        });

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.add(toolbar, BorderLayout.NORTH);
        body.add(listScroll, BorderLayout.CENTER);

        panel.add(body, BorderLayout.CENTER);
        refreshStatus();
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
