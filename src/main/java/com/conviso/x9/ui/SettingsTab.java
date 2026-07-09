package com.conviso.x9.ui;

import com.conviso.x9.ConvisoExtension;
import com.conviso.x9.model.AiProviderOption;
import com.conviso.x9.model.AssetItem;
import com.conviso.x9.model.ProjectItem;
import com.conviso.x9.settings.ExtensionSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds and owns the "Settings > Configuration" tab: credentials, AI provider, and project picker. */
public final class SettingsTab {

    private static final String EXTENSION_VERSION = "1.0.0";

    private final ConvisoExtension extension;

    private final JComponent panel;
    private JPasswordField apiKeyField;
    private JPasswordField aiApiKeyField;
    private JComboBox<AiProviderOption> aiProviderCombo;
    private JCheckBox englishReportCheckbox;
    private JTextField companyIdField;
    private JComboBox<ProjectItem> projectCombo;
    private JButton testButton;
    private JButton testAiButton;
    private JButton loadProjectsButton;

    private final List<ProjectItem> availableProjects = new ArrayList<>();
    private boolean suppressProjectSelectionEvent;
    private boolean filteringProjectCombo;

    public SettingsTab(ConvisoExtension extension) {
        this.extension = extension;
        this.panel = build();
    }

    public JComponent getPanel() {
        return panel;
    }

    public String readApiKey() {
        return apiKeyField == null ? "" : new String(apiKeyField.getPassword()).trim();
    }

    public String readAiApiKey() {
        return aiApiKeyField == null ? "" : new String(aiApiKeyField.getPassword()).trim();
    }

    public String currentSummaryLanguage() {
        return englishReportCheckbox != null && englishReportCheckbox.isSelected() ? "en" : "pt";
    }

    public String currentAiProviderId() {
        Object selected = aiProviderCombo == null ? null : aiProviderCombo.getSelectedItem();
        if (selected instanceof AiProviderOption) {
            return safe(((AiProviderOption) selected).getId());
        }
        return "gemini";
    }

    public String currentCompanyId() {
        if (companyIdField != null) {
            String typed = safe(companyIdField.getText()).trim();
            if (!typed.isEmpty()) {
                return typed;
            }
        }
        return extension.getSettings().getScopeId();
    }

    public ProjectItem getSelectedProject() {
        Object selected = projectCombo == null ? null : projectCombo.getSelectedItem();
        return selected instanceof ProjectItem ? (ProjectItem) selected : null;
    }

    public void selectProjectById(String projectId) {
        if (projectCombo == null) {
            return;
        }
        for (int i = 0; i < projectCombo.getItemCount(); i++) {
            ProjectItem item = projectCombo.getItemAt(i);
            if (item != null && item.getId().equals(projectId)) {
                projectCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    public void persistCredentials() {
        ExtensionSettings settings = extension.getSettings();
        settings.setApiKey(readApiKey());
        settings.setAiApiKey(readAiApiKey());
        settings.setAiProvider(currentAiProviderId());
        settings.setReportLanguage(currentSummaryLanguage());
        settings.setScopeId(companyIdField.getText().trim());
    }

    public void fillProjects(JsonArray projects) {
        if (projectCombo == null) {
            return;
        }
        String selectedProjectId = extension.currentProjectId();
        availableProjects.clear();

        for (int i = 0; i < projects.size(); i++) {
            JsonObject obj = projects.get(i).getAsJsonObject();
            String id = getString(obj, "id");
            String label = getString(obj, "label");
            String pid = getString(obj, "pid");
            StringBuilder display = new StringBuilder(id);
            if (!pid.isEmpty()) {
                display.append(" - ").append(pid);
            }
            if (!label.isEmpty()) {
                display.append(" - ").append(label);
            }
            availableProjects.add(new ProjectItem(id, display.toString(), label, pid, parseAssets(obj)));
        }

        suppressProjectSelectionEvent = true;
        try {
            projectCombo.removeAllItems();
            for (ProjectItem item : availableProjects) {
                projectCombo.addItem(item);
            }
            if (!selectedProjectId.isEmpty()) {
                selectProjectById(selectedProjectId);
            } else if (projectCombo.getItemCount() > 0) {
                projectCombo.setSelectedIndex(0);
            }
        } finally {
            suppressProjectSelectionEvent = false;
        }
    }

    private JComponent build() {
        JTabbedPane settingsTabs = new JTabbedPane();
        settingsTabs.addTab("Configuration", buildConfigurationPanel());
        return settingsTabs;
    }

    private JPanel buildConfigurationPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel centered = new JPanel();
        centered.setLayout(new BoxLayout(centered, BoxLayout.Y_AXIS));
        centered.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        centered.setBackground(new Color(245, 247, 250));

        JLabel subtitle = new JLabel("Burp Suite Plugin For Conviso Platform", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel version = new JLabel("Version: " + EXTENSION_VERSION, SwingConstants.CENTER);
        version.setFont(new Font("SansSerif", Font.PLAIN, 14));
        version.setAlignmentX(Component.CENTER_ALIGNMENT);

        centered.add(buildLogoHeader());
        centered.add(Box.createRigidArea(new Dimension(0, 12)));
        centered.add(subtitle);
        centered.add(Box.createRigidArea(new Dimension(0, 4)));
        centered.add(version);
        centered.add(Box.createRigidArea(new Dimension(0, 24)));
        centered.add(buildConfigurationForm());

        panel.add(centered, BorderLayout.NORTH);
        return panel;
    }

    private JComponent buildConfigurationForm() {
        ExtensionSettings settings = extension.getSettings();

        JPanel form = new JPanel(new GridBagLayout());
        form.setMaximumSize(new Dimension(1100, 300));
        form.setOpaque(true);
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(222, 226, 230)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        apiKeyField = new JPasswordField(settings.getApiKey(), 60);
        aiApiKeyField = new JPasswordField(settings.getAiApiKey(), 60);
        aiProviderCombo = new JComboBox<>();
        aiProviderCombo.addItem(new AiProviderOption("openai", "OpenAI"));
        aiProviderCombo.addItem(new AiProviderOption("claude", "Claude"));
        aiProviderCombo.addItem(new AiProviderOption("gemini", "Gemini"));
        selectAiProviderById(settings.getAiProvider().isEmpty() ? "gemini" : settings.getAiProvider());
        englishReportCheckbox = new JCheckBox("English (Unchecked = Portuguese)");
        englishReportCheckbox.setSelected("en".equalsIgnoreCase(settings.getReportLanguage()));
        englishReportCheckbox.setOpaque(false);
        companyIdField = new JTextField(settings.getScopeId(), 20);
        projectCombo = new JComboBox<>();
        projectCombo.setEditable(true);
        projectCombo.setPrototypeDisplayValue(new ProjectItem("99999", "99999 - PRJ-99999 - Very Large Example Project", "Very Large Example Project", "PRJ-99999"));

        String savedProjectId = settings.getProjectId();
        if (!savedProjectId.isEmpty()) {
            projectCombo.addItem(new ProjectItem(savedProjectId, savedProjectId + " - saved project", "", ""));
            projectCombo.setSelectedIndex(0);
        }

        testButton = new JButton("Test");
        testAiButton = new JButton("Test AI");
        loadProjectsButton = new JButton("Load Projects");
        extension.registerBusyButton(testButton);
        extension.registerBusyButton(testAiButton);
        extension.registerBusyButton(loadProjectsButton);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("API Key"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(apiKeyField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        form.add(testButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("AI API Key"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 1;
        form.add(aiApiKeyField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        form.add(testAiButton, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        form.add(new JLabel("AI Provider"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        form.add(aiProviderCombo, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        form.add(new JLabel("Summary Language"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        form.add(englishReportCheckbox, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        form.add(new JLabel("Company ID"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        form.add(companyIdField, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        form.add(new JLabel("Project"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(projectCombo, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        form.add(loadProjectsButton, gbc);

        testButton.addActionListener(e -> extension.testConnection());
        testAiButton.addActionListener(e -> extension.testAiConnection());
        loadProjectsButton.addActionListener(e -> extension.loadProjects());
        projectCombo.addActionListener(e -> {
            if (!suppressProjectSelectionEvent) {
                extension.onProjectSelectionChanged();
            }
        });
        installProjectComboFiltering();
        aiProviderCombo.addActionListener(e -> extension.persistSettings());
        englishReportCheckbox.addActionListener(e -> extension.persistSettings());

        return form;
    }

    private JComponent buildLogoHeader() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(360, 140));
        wrapper.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        try {
            InputStream in = SettingsTab.class.getResourceAsStream("/assets/conviso-logo.png");
            if (in != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(in));
                JLabel logoLabel = new JLabel(icon);
                logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
                wrapper.add(logoLabel, BorderLayout.CENTER);
                return wrapper;
            }
        } catch (Exception ignored) {
            // Falls back to vector rendering when the image resource is unavailable.
        }

        wrapper.add(new ConvisoLogoPanel(), BorderLayout.CENTER);
        return wrapper;
    }

    private void selectAiProviderById(String providerId) {
        String wanted = safe(providerId).toLowerCase(Locale.ROOT);
        for (int i = 0; i < aiProviderCombo.getItemCount(); i++) {
            AiProviderOption option = aiProviderCombo.getItemAt(i);
            if (option != null && safe(option.getId()).equals(wanted)) {
                aiProviderCombo.setSelectedIndex(i);
                return;
            }
        }
        aiProviderCombo.setSelectedIndex(2);
    }

    private void installProjectComboFiltering() {
        Component editor = projectCombo.getEditor().getEditorComponent();
        if (!(editor instanceof JTextField)) {
            return;
        }
        JTextField projectEditor = (JTextField) editor;
        projectEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterProjectsFromEditor(projectEditor);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterProjectsFromEditor(projectEditor);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterProjectsFromEditor(projectEditor);
            }
        });
    }

    private void filterProjectsFromEditor(JTextField projectEditor) {
        if (suppressProjectSelectionEvent || !projectEditor.hasFocus()) {
            return;
        }
        SwingUtilities.invokeLater(() -> filterProjects(projectEditor.getText()));
    }

    private void filterProjects(String pattern) {
        if (filteringProjectCombo) {
            return;
        }
        filteringProjectCombo = true;
        suppressProjectSelectionEvent = true;
        try {
            String selectedProjectId = extension.currentProjectId();
            String loweredPattern = safe(pattern).trim().toLowerCase(Locale.ROOT);

            projectCombo.removeAllItems();
            for (ProjectItem item : availableProjects) {
                String haystack = (safe(item.getDisplay()) + " " + safe(item.getLabel()) + " " + safe(item.getPid()) + " " + safe(item.getId())).toLowerCase(Locale.ROOT);
                if (loweredPattern.isEmpty() || haystack.contains(loweredPattern)) {
                    projectCombo.addItem(item);
                }
            }

            if (!selectedProjectId.isEmpty()) {
                selectProjectById(selectedProjectId);
            }

            Component editor = projectCombo.getEditor().getEditorComponent();
            if (editor instanceof JTextField) {
                ((JTextField) editor).setText(pattern);
            }
            projectCombo.setPopupVisible(projectCombo.getItemCount() > 0 && !loweredPattern.isEmpty());
        } finally {
            suppressProjectSelectionEvent = false;
            filteringProjectCombo = false;
        }
    }

    private static List<AssetItem> parseAssets(JsonObject projectObj) {
        List<AssetItem> assets = new ArrayList<>();
        if (projectObj == null || !projectObj.has("assets") || !projectObj.get("assets").isJsonArray()) {
            return assets;
        }
        JsonArray assetsArray = projectObj.getAsJsonArray("assets");
        for (int i = 0; i < assetsArray.size(); i++) {
            if (assetsArray.get(i) != null && assetsArray.get(i).isJsonObject()) {
                JsonObject assetObj = assetsArray.get(i).getAsJsonObject();
                assets.add(new AssetItem(getString(assetObj, "id"), getString(assetObj, "name")));
            }
        }
        return assets;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
