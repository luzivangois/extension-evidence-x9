package com.conviso.x9;

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IContextMenuFactory;
import burp.IContextMenuInvocation;
import burp.IExtensionHelpers;
import burp.IExtensionStateListener;
import burp.IHttpRequestResponse;
import burp.IRequestInfo;
import burp.ITab;
import com.conviso.x9.ai.AiService;
import com.conviso.x9.ai.AiServiceException;
import com.conviso.x9.api.ConvisoApiClient;
import com.conviso.x9.api.ConvisoApiException;
import com.conviso.x9.evidence.EvidenceExtractor;
import com.conviso.x9.evidence.EvidenceScreenshotRenderer;
import com.conviso.x9.evidence.HttpEvidence;
import com.conviso.x9.logging.ExtensionLogger;
import com.conviso.x9.model.AssetItem;
import com.conviso.x9.model.ProjectItem;
import com.conviso.x9.model.RequirementItem;
import com.conviso.x9.model.VulnerabilityDraft;
import com.conviso.x9.model.VulnerabilityRecord;
import com.conviso.x9.model.VulnerabilityTemplateDetail;
import com.conviso.x9.model.VulnerabilityTemplateSummary;
import com.conviso.x9.model.X9Item;
import com.conviso.x9.requirement.RequirementMatcher;
import com.conviso.x9.settings.ExtensionSettings;
import com.conviso.x9.ui.RequirementsTab;
import com.conviso.x9.ui.SettingsTab;
import com.conviso.x9.ui.VulnerabilitiesTab;
import com.conviso.x9.ui.VulnerabilityDialog;
import com.conviso.x9.ui.X9Tab;
import com.conviso.x9.vulnerability.VulnerabilityClassifier;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Extension entry point and coordinator: owns cross-tab state (settings, API
 * clients, message references) and the business flows that span more than
 * one tab. Swing layout for each tab lives in {@code com.conviso.x9.ui}.
 */
public final class ConvisoExtension implements IBurpExtender, IContextMenuFactory, ITab, IExtensionStateListener {

    private static final String EXTENSION_NAME = "Conviso Platform";
    private static final int STANDARD_MIN_TOKEN_LENGTH = 6;

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private ExtensionLogger logger;
    private ExtensionSettings settings;

    private final ConvisoApiClient apiClient = new ConvisoApiClient();
    private final AiService aiService = new AiService();
    private EvidenceExtractor evidenceExtractor;
    private final Gson gson = new Gson();

    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "conviso-x9-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, IHttpRequestResponse> x9MessageRefs = new HashMap<>();
    private final Map<String, VulnerabilityRecord> x9VulnerabilityRefs = new HashMap<>();
    private final List<AbstractButton> busyButtons = new ArrayList<>();

    private JPanel rootPanel;
    private JTabbedPane mainTabs;
    private SettingsTab settingsTab;
    private RequirementsTab requirementsTab;
    private X9Tab x9Tab;
    private VulnerabilitiesTab vulnerabilitiesTab;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.logger = new ExtensionLogger(new PrintWriter(callbacks.getStdout(), true));
        this.settings = new ExtensionSettings(callbacks);
        this.evidenceExtractor = new EvidenceExtractor(helpers);

        callbacks.setExtensionName(EXTENSION_NAME);
        callbacks.registerContextMenuFactory(this);
        callbacks.registerExtensionStateListener(this);

        buildUi();
        callbacks.addSuiteTab(this);

        logger.info("[+] Conviso Platform extension loaded");
    }

    @Override
    public void extensionUnloaded() {
        backgroundExecutor.shutdownNow();
    }

    @Override
    public String getTabCaption() {
        return EXTENSION_NAME;
    }

    @Override
    public Component getUiComponent() {
        return rootPanel;
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        JMenu root = new JMenu("Conviso Platform");
        JMenuItem sendCurrent = new JMenuItem("Organize");
        sendCurrent.addActionListener(e -> organizeSelectedTest(invocation));
        root.add(sendCurrent);

        JMenuItem createVuln = new JMenuItem("Create Vuln");
        createVuln.addActionListener(e -> createVulnerabilityFromSelection(invocation));
        root.add(createVuln);

        JMenu requirementsMenu = buildRequirementsMenu(requirement -> stageInX9(invocation, requirement.getId()));
        root.add(requirementsMenu);

        List<JMenuItem> items = new ArrayList<>();
        items.add(root);
        return items;
    }

    /** Builds a "Requirements" submenu listing the loaded catalog, shared between the Proxy/Repeater context menu and the Vulnerabilities tab's own context menu. */
    public JMenu buildRequirementsMenu(Consumer<RequirementItem> onRequirementSelected) {
        JMenu requirementsMenu = new JMenu("Requirements");
        List<RequirementItem> catalog = requirementCatalog();
        if (!catalog.isEmpty()) {
            for (RequirementItem requirement : catalog) {
                JMenuItem requirementItem = new JMenuItem(requirement.toString());
                requirementItem.addActionListener(e -> onRequirementSelected.accept(requirement));
                requirementsMenu.add(requirementItem);
            }
        } else {
            JMenuItem empty = new JMenuItem("No Requirements Loaded");
            empty.setEnabled(false);
            requirementsMenu.add(empty);
        }
        return requirementsMenu;
    }

    // ------------------------------------------------------------------
    // Shared accessors used by the UI tabs
    // ------------------------------------------------------------------

    public ExtensionSettings getSettings() {
        return settings;
    }

    public ExtensionLogger getLogger() {
        return logger;
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public void registerBusyButton(AbstractButton button) {
        busyButtons.add(button);
    }

    public String currentProjectId() {
        ProjectItem project = settingsTab.getSelectedProject();
        return project != null ? project.getId() : settings.getProjectId();
    }

    /** Human-friendly label for the currently selected project, for read-only display (e.g. the X9 tab). */
    public String currentProjectDisplay() {
        ProjectItem project = settingsTab.getSelectedProject();
        if (project != null) {
            return project.getDisplay();
        }
        String projectId = settings.getProjectId();
        return projectId.isEmpty() ? "(no project selected)" : projectId;
    }

    public String currentRequirementId() {
        RequirementItem requirement = requirementsTab.selectedRequirement();
        return requirement == null ? "" : requirement.getId();
    }

    public String currentCompanyId() {
        return settingsTab.currentCompanyId();
    }

    public List<RequirementItem> requirementCatalog() {
        List<RequirementItem> catalog = new ArrayList<>();
        for (int i = 0; i < requirementsTab.getModel().size(); i++) {
            catalog.add(requirementsTab.getModel().get(i));
        }
        return catalog;
    }

    public RequirementItem findRequirementById(String requirementId) {
        for (RequirementItem requirement : requirementCatalog()) {
            if (requirement.getId().equals(requirementId)) {
                return requirement;
            }
        }
        return null;
    }

    public void appendOutput(String text) {
        requirementsTab.appendOutput(text);
    }

    public void refreshX9Views() {
        if (x9Tab != null) {
            x9Tab.refreshViews();
        }
    }

    public void persistSettings() {
        settingsTab.persistCredentials();
        ProjectItem project = settingsTab.getSelectedProject();
        if (project != null) {
            settings.setProjectId(project.getId());
        }
        RequirementItem requirement = requirementsTab.selectedRequirement();
        if (requirement != null) {
            settings.setRequirementId(requirement.getId());
        }
    }

    // ------------------------------------------------------------------
    // UI assembly
    // ------------------------------------------------------------------

    private void buildUi() {
        rootPanel = new JPanel(new BorderLayout());

        mainTabs = new JTabbedPane();
        // settingsTab must exist before the other tabs: X9Tab (and potentially others) read
        // the currently selected project from it as soon as they're constructed.
        settingsTab = new SettingsTab(this);
        vulnerabilitiesTab = new VulnerabilitiesTab(this);
        requirementsTab = new RequirementsTab(this);
        x9Tab = new X9Tab(this);

        mainTabs.addTab("Vulnerabilities", vulnerabilitiesTab.getPanel());
        mainTabs.addTab("Requirements", requirementsTab.getPanel());
        mainTabs.addTab("X9", x9Tab.getPanel());
        mainTabs.addTab("Settings", settingsTab.getPanel());

        rootPanel.add(mainTabs, BorderLayout.CENTER);

        loadX9ItemsFromSettings();
        loadVulnerabilityItemsFromSettings();
    }

    // ------------------------------------------------------------------
    // Settings tab actions
    // ------------------------------------------------------------------

    public void testConnection() {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Enter the API Key.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Testing connection to the API...");
        backgroundExecutor.submit(() -> {
            try {
                apiClient.fetchProjects(apiKey, settingsTab.currentCompanyId(), 1);
                persistSettings();
                appendOutput("[+] Connection validated successfully.");
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Test failed: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void testAiConnection() {
        String aiApiKey = settingsTab.readAiApiKey();
        if (aiApiKey.isEmpty()) {
            showMessage("Enter the AI API Key.");
            return;
        }

        String provider = settingsTab.currentAiProviderId();
        setBusy(true);
        appendOutput("[+] Testing connection to " + provider + "...");
        backgroundExecutor.submit(() -> {
            try {
                aiService.validateApiKey(aiApiKey, provider);
                persistSettings();
                appendOutput("[+] " + provider + " validated successfully.");
            } catch (AiServiceException ex) {
                appendOutput("[!] AI test failed (" + provider + "): " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void loadProjects() {
        String apiKey = settingsTab.readApiKey();
        String scopeId = settingsTab.currentCompanyId();

        if (apiKey.isEmpty()) {
            showMessage("Enter the API Key.");
            return;
        }
        if (scopeId.isEmpty()) {
            showMessage("Enter the Company ID.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Loading projects...");
        backgroundExecutor.submit(() -> {
            try {
                JsonArray projects = apiClient.fetchProjects(apiKey, scopeId, 100);
                SwingUtilities.invokeLater(() -> settingsTab.fillProjects(projects));
                persistSettings();
                appendOutput("[+] Projects loaded: " + projects.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Error loading projects: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void onProjectSelectionChanged() {
        ProjectItem item = settingsTab.getSelectedProject();
        if (item != null) {
            settings.setProjectId(item.getId());
            settings.setRequirementId("");
        }
        refreshX9Views();
        vulnerabilitiesTab.refreshStatus();
    }

    // ------------------------------------------------------------------
    // Requirements tab actions
    // ------------------------------------------------------------------

    public void loadRequirements() {
        String apiKey = settingsTab.readApiKey();
        ProjectItem project = settingsTab.getSelectedProject();

        if (apiKey.isEmpty()) {
            showMessage("Enter the API Key.");
            return;
        }
        if (project == null) {
            showMessage("Select a project in the Settings tab.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Loading requirements for project " + project.getId() + "...");
        backgroundExecutor.submit(() -> {
            try {
                JsonArray requirements = apiClient.fetchRequirements(apiKey, project.getId());
                SwingUtilities.invokeLater(() -> requirementsTab.fillRequirements(requirements));
                appendOutput("[+] Requirements loaded: " + requirements.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Error loading requirements: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    // ------------------------------------------------------------------
    // Vulnerabilities tab actions
    // ------------------------------------------------------------------

    public void loadProjectVulnerabilities() {
        String apiKey = settingsTab.readApiKey();
        String projectId = currentProjectId();
        if (apiKey.isEmpty()) {
            showMessage("Enter the API Key.");
            return;
        }
        if (projectId.isEmpty()) {
            showMessage("Select a project in Settings > Configuration.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Loading vulnerabilities for project " + projectId + "...");
        backgroundExecutor.submit(() -> {
            try {
                JsonArray vulnerabilities = apiClient.fetchProjectVulnerabilities(apiKey, projectId, currentCompanyId());
                SwingUtilities.invokeLater(() -> {
                    vulnerabilitiesTab.clearModel();
                    for (int i = 0; i < vulnerabilities.size(); i++) {
                        if (vulnerabilities.get(i) != null && vulnerabilities.get(i).isJsonObject()) {
                            VulnerabilityRecord record = apiClient.parseVulnerabilityRecord(vulnerabilities.get(i).getAsJsonObject(), projectId);
                            classifyByTemplate(record);
                            vulnerabilitiesTab.addOrMergeRecord(record);
                        }
                    }
                    loadVulnerabilityItemsFromSettings();
                    vulnerabilitiesTab.refreshStatus();
                });
                appendOutput("[+] Vulnerabilities loaded: " + vulnerabilities.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Error loading vulnerabilities: " + ex.getMessage());
                showMessage("Failed to load vulnerabilities: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    /**
     * Stages a vulnerability-to-requirement link as an X9 draft, chosen from the Vulnerabilities tab's
     * own "Requirements" context menu — mirrors {@link #stageInX9} (Proxy/Repeater context menu): nothing
     * is sent to the platform here, only queued in X9 for review/send like any other draft. Only the
     * summary text and evidence source differ (Title/Description/Severity PNG instead of a live Burp
     * request/response).
     */
    public void stageVulnerabilityInX9(VulnerabilityRecord record, String requirementId) {
        if (record == null) {
            showMessage("Select a vulnerability.");
            return;
        }

        String projectId = currentProjectId();
        if (projectId.isEmpty()) {
            showMessage("Select a project in Settings > Configuration.");
            return;
        }
        if (requirementId == null || requirementId.isEmpty()) {
            showMessage("Select a requirement.");
            return;
        }

        RequirementItem requirement = findRequirementById(requirementId);
        String requirementTitle = requirement != null ? requirement.getTitle() : requirementId;
        String summary = buildVulnerabilityLinkSummary(requirementTitle, record, settingsTab.currentSummaryLanguage());

        addVulnerabilityX9DraftItem(projectId, requirementId, summary, record);
        settings.setProjectId(projectId);
        settings.setRequirementId(requirementId);

        appendOutput("[+] Requirement " + requirementId + " was sent to X9 as a draft (linked to vulnerability).");
        mainTabs.setSelectedIndex(2);
    }

    /** Mirrors the Settings > Configuration "Summary Language" toggle that already drives every AI-generated field sent to the platform, so this hand-written comment matches it too. */
    private String buildVulnerabilityLinkSummary(String requirementTitle, VulnerabilityRecord record, String language) {
        String vulnerabilityTitle = safe(record.getTitle());
        String vulnerabilityUrl = buildVulnerabilityFullUrl(record);
        if ("en".equalsIgnoreCase(language)) {
            return "During the tests performed for the context of this requirement (" + requirementTitle
                + "), the vulnerability " + vulnerabilityTitle
                + " was identified, which was reported and registered at " + vulnerabilityUrl;
        }
        return "Dentro dos testes realizados para o contexto deste requirement de " + requirementTitle
            + " foi identificada a vulnerabilidade de " + vulnerabilityTitle
            + ", a qual foi reportada e registrada em " + vulnerabilityUrl;
    }

    private String buildVulnerabilityFullUrl(VulnerabilityRecord record) {
        return "https://app.convisoappsec.com/spa/company/" + currentCompanyId() + "/vulnerabilities/" + safe(record.getId());
    }

    // ------------------------------------------------------------------
    // X9 tab actions
    // ------------------------------------------------------------------

    public void saveSelectedX9Summary() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Select an item in X9.");
            return;
        }
        item.setSummary(x9Tab.getSummaryText());
        x9Tab.notifyItemChanged(item);
        saveX9Items();
        x9Tab.setStatus("Summary saved for requirement " + item.getRequirementId() + ".");
    }

    public void refreshSelectedX9WithAi() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Select an item in X9.");
            return;
        }

        IHttpRequestResponse message = x9MessageRefs.get(X9Tab.keyOf(item));
        setBusy(true);
        backgroundExecutor.submit(() -> {
            try {
                RequirementItem requirement = findRequirementById(item.getRequirementId());
                HttpEvidence evidence = evidenceExtractor.extract(message);
                String refreshedSummary = aiService.buildSummary(
                    settingsTab.readAiApiKey(), settingsTab.currentAiProviderId(), settingsTab.currentSummaryLanguage(),
                    evidence, requirement
                );
                SwingUtilities.invokeLater(() -> {
                    item.setSummary(safe(refreshedSummary).trim());
                    x9Tab.notifyItemChanged(item);
                    saveX9Items();
                    x9Tab.setStatus("Summary updated with AI for requirement " + item.getRequirementId() + ".");
                });
            } finally {
                setBusy(false);
            }
        });
    }

    public void sendSelectedX9() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Select an item in X9.");
            return;
        }
        item.setSummary(x9Tab.getSummaryText());
        publishX9Item(item);
    }

    public void sendAllPendingX9() {
        List<X9Item> pending = new ArrayList<>();
        for (X9Item item : x9Tab.allItems()) {
            if (!"SENT".equals(item.getState())) {
                pending.add(item);
            }
        }
        if (pending.isEmpty()) {
            showMessage("There are no pending items in X9.");
            return;
        }
        for (X9Item item : pending) {
            publishX9Item(item);
        }
    }

    public void deleteSelectedX9Item() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Select an item in X9 to delete.");
            return;
        }

        String state = safe(item.getState()).isEmpty() ? "DRAFT" : item.getState();
        int decision = JOptionPane.showConfirmDialog(
            rootPanel,
            "Delete requirement " + item.getRequirementId() + " from X9? Current state: " + state,
            "Confirm Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (decision != JOptionPane.YES_OPTION) {
            return;
        }

        String key = X9Tab.keyOf(item);
        x9Tab.removeItemByKey(key);
        x9MessageRefs.remove(key);
        x9VulnerabilityRefs.remove(key);
        saveX9Items();
        appendOutput("[+] Requirement " + item.getRequirementId() + " removed from X9.");
    }

    private void publishX9Item(X9Item item) {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Set the API Key in Settings > Configuration.");
            return;
        }
        if (safe(item.getSummary()).trim().isEmpty()) {
            showMessage("Empty summary for requirement " + item.getRequirementId() + ".");
            return;
        }
        if (!ensureRequirementRunning(item.getRequirementId())) {
            return;
        }

        String key = X9Tab.keyOf(item);
        IHttpRequestResponse message = x9MessageRefs.get(key);
        VulnerabilityRecord vulnerabilityRecord = x9VulnerabilityRefs.get(key);
        if (message == null && vulnerabilityRecord == null) {
            showMessage("No evidence available for requirement " + item.getRequirementId()
                + " (the original reference was lost, likely due to a Burp restart).");
            return;
        }

        boolean markAsDone = item.isMarkAsDone();

        setBusy(true);
        backgroundExecutor.submit(() -> {
            try {
                byte[] evidencePng;
                String fileName;
                if (message != null) {
                    evidencePng = renderRequestResponseEvidence(message);
                    fileName = "evidence.png";
                } else {
                    // The vulnerability must already exist on the Conviso Platform in this flow (it was
                    // either just created there or loaded via "Load Project Vulnerabilities"), so the
                    // locally cached record may be missing description/summary — the Issues list query
                    // never requests those fields. Always re-fetch the full detail from the platform.
                    VulnerabilityRecord detail = apiClient.fetchVulnerabilityDetail(apiKey, vulnerabilityRecord.getId());
                    evidencePng = EvidenceScreenshotRenderer.renderVulnerabilitySummaryPng(
                        safe(detail.getTitle()), safe(detail.getDescription()), safe(detail.getEvidence()), safe(detail.getSeverity())
                    );
                    fileName = "VulnerabilitySummary-" + safe(vulnerabilityRecord.getId()) + ".png";
                }

                if (markAsDone) {
                    apiClient.markRequirementDone(apiKey, item.getRequirementId(), item.getSummary(), evidencePng, fileName);
                } else {
                    apiClient.addRequirementAttachment(apiKey, item.getRequirementId(), item.getSummary(), evidencePng, fileName);
                }

                item.setState("SENT");
                item.setSentAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                item.setApprovedBy(System.getProperty("user.name", "analyst"));
                SwingUtilities.invokeLater(() -> x9Tab.notifyItemChanged(item));

                if (message != null) {
                    markAsSentInBurp(message, item.getRequirementId());
                }
                appendOutput("[+] Requirement " + item.getRequirementId() + " sent to platform via X9"
                    + (markAsDone ? " (status changed to Done)." : " (status remains Running)."));
            } catch (IOException | ConvisoApiException ex) {
                appendOutput("[!] Error sending requirement " + item.getRequirementId() + ": " + ex.getMessage());
            } finally {
                setBusy(false);
                saveX9Items();
            }
        });
    }

    private byte[] renderRequestResponseEvidence(IHttpRequestResponse message) throws IOException {
        HttpEvidence evidence = evidenceExtractor.extract(message);
        return EvidenceScreenshotRenderer.renderRequestResponsePng(
            evidence.getMethod(), evidence.getUrl(), evidence.getFullRequest(), evidence.getFullResponse()
        );
    }

    /** The extension only allows attaching evidence while a requirement is Running (IN_PROGRESS) on the platform, matching the platform's own rule. */
    private boolean ensureRequirementRunning(String requirementId) {
        RequirementItem requirement = findRequirementById(requirementId);
        if (requirement == null) {
            showMessage("Requirement " + requirementId + " not found in the loaded catalog. Reload the requirements in Requirements > Load Requirements.");
            return false;
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(safe(requirement.getStatus()))) {
            showMessage("Requirement " + requirementId + " needs to be Running on the Conviso Platform to receive evidence (current status: "
                + requirement.getStatus() + "). Change the status on the platform and reload the requirements.");
            return false;
        }
        return true;
    }

    /** Lets UI classes persist X9 item edits (e.g. the "Done" checkbox) without exposing the local JSON storage details. */
    public void persistX9Items() {
        saveX9Items();
    }

    // ------------------------------------------------------------------
    // Context-menu driven flows
    // ------------------------------------------------------------------

    public void stageInX9(IContextMenuInvocation invocation, String forcedRequirementId) {
        IHttpRequestResponse selectedMessage = getFirstSelectedMessage(invocation);
        String[] ids = invocation != null ? extractIdsFromSelectedRequest(invocation) : new String[]{"", ""};

        String projectId = !ids[0].isEmpty() ? ids[0] : currentProjectId();
        String requirementId = !forcedRequirementId.isEmpty()
            ? forcedRequirementId
            : (!ids[1].isEmpty() ? ids[1] : currentRequirementId());

        if (projectId.isEmpty()) {
            showMessage("Select a project in Settings > Configuration.");
            return;
        }
        if (requirementId.isEmpty()) {
            showMessage("Select a requirement in the Requirements tab.");
            return;
        }

        RequirementItem requirement = findRequirementById(requirementId);
        HttpEvidence evidence = evidenceExtractor.extract(selectedMessage);
        String summary = aiService.buildSummary(
            settingsTab.readAiApiKey(), settingsTab.currentAiProviderId(), settingsTab.currentSummaryLanguage(),
            evidence, requirement
        );
        if (selectedMessage != null) {
            // A real Burp message is being staged (context-menu action): always create a
            // new draft, even if the requirement already has one from a different request.
            addX9DraftItem(projectId, requirementId, summary, selectedMessage);
        } else {
            // No message (e.g. just clicking a requirement in the Requirements tab list):
            // reuse/refresh the requirement's current placeholder draft instead of piling up empties.
            upsertX9Item(projectId, requirementId, summary, null);
        }
        markAsRequirementDraftInBurp(selectedMessage);
        settings.setProjectId(projectId);
        settings.setRequirementId(requirementId);

        appendOutput("[+] Requirement " + requirementId + " was sent to X9 as a draft.");
        mainTabs.setSelectedIndex(2);
    }

    private void organizeSelectedTest(IContextMenuInvocation invocation) {
        IHttpRequestResponse[] selectedMessages = getSelectedMessages(invocation);
        if (selectedMessages.length == 0) {
            showMessage("Select one or more messages in the Proxy to organize.");
            return;
        }

        String projectFromSettings = currentProjectId();
        if (projectFromSettings.isEmpty()) {
            showMessage("Select a project in Settings > Configuration.");
            return;
        }

        if (requirementCatalog().isEmpty()) {
            String apiKey = settingsTab.readApiKey();
            if (apiKey.isEmpty()) {
                showMessage("Set the API Key and load the requirements before organizing.");
                return;
            }
            try {
                JsonArray requirements = apiClient.fetchRequirements(apiKey, projectFromSettings);
                requirementsTab.fillRequirements(requirements);
                appendOutput("[+] Requirements loaded automatically for Organize.");
            } catch (ConvisoApiException ex) {
                showMessage("Failed to load requirements automatically: " + ex.getMessage());
                return;
            }
        }

        int organizedCount = 0;
        int skippedCount = 0;
        String lastProjectId = "";
        String lastRequirementId = "";

        for (IHttpRequestResponse message : selectedMessages) {
            if (message == null) {
                skippedCount++;
                continue;
            }

            String[] ids = extractIdsFromMessage(message);
            String projectId = !ids[0].isEmpty() ? ids[0] : projectFromSettings;
            if (projectId.isEmpty()) {
                skippedCount++;
                continue;
            }

            String requirementId = determineRequirementForOrganize(message, ids[1]);
            if (requirementId.isEmpty()) {
                skippedCount++;
                continue;
            }

            RequirementItem requirement = findRequirementById(requirementId);
            HttpEvidence evidence = evidenceExtractor.extract(message);
            String summary = aiService.buildSummary(
                settingsTab.readAiApiKey(), settingsTab.currentAiProviderId(), settingsTab.currentSummaryLanguage(),
                evidence, requirement
            );
            addX9DraftItem(projectId, requirementId, summary, message);
            markAsRequirementDraftInBurp(message);

            organizedCount++;
            lastProjectId = projectId;
            lastRequirementId = requirementId;
        }

        if (organizedCount == 0) {
            showMessage("No item was organized. Check whether the requirements are loaded.");
            return;
        }

        settings.setProjectId(lastProjectId);
        settings.setRequirementId(lastRequirementId);

        appendOutput("[+] Organize complete: " + organizedCount + " item(s) organized, " + skippedCount + " skipped.");
        mainTabs.setSelectedIndex(2);
    }

    private String determineRequirementForOrganize(IHttpRequestResponse message, String preselectedRequirementId) {
        if (!safe(preselectedRequirementId).isEmpty() && findRequirementById(preselectedRequirementId) != null) {
            return preselectedRequirementId;
        }
        List<RequirementItem> catalog = requirementCatalog();
        if (catalog.isEmpty()) {
            return "";
        }

        String aiApiKey = settingsTab.readAiApiKey();
        if (!aiApiKey.isEmpty()) {
            try {
                HttpEvidence evidence = evidenceExtractor.extract(message);
                String aiChoice = aiService.classifyRequirement(aiApiKey, settingsTab.currentAiProviderId(), evidence, catalog);
                if (!aiChoice.isEmpty() && findRequirementById(aiChoice) != null) {
                    return aiChoice;
                }
            } catch (AiServiceException ex) {
                appendOutput("[!] AI classification unavailable, using fallback: " + ex.getMessage());
            }
        }

        String evidenceText = evidenceExtractor.extract(message).asSearchableText();
        Optional<String> matched = RequirementMatcher.matchByDescriptionTokens(evidenceText, catalog, STANDARD_MIN_TOKEN_LENGTH);
        return matched.orElseGet(() -> RequirementMatcher.firstAvailable(catalog));
    }

    private void createVulnerabilityFromSelection(IContextMenuInvocation invocation) {
        IHttpRequestResponse selectedMessage = getFirstSelectedMessage(invocation);
        if (selectedMessage == null) {
            showMessage("Select a message in the Proxy to create a vulnerability.");
            return;
        }

        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Set the API Key in Settings > Configuration.");
            return;
        }

        String projectId = currentProjectId();
        if (projectId.isEmpty()) {
            showMessage("Select a project in Settings > Configuration.");
            return;
        }

        HttpEvidence evidence = evidenceExtractor.extract(selectedMessage);
        VulnerabilityDraft draft = new VulnerabilityDraft();
        draft.setProjectId(projectId);
        draft.setMethod(evidence.getMethod());
        draft.setUrl(evidence.getUrl());
        draft.setScheme(evidence.getScheme());
        draft.setPort(evidence.getPort());
        draft.setParameters(evidence.getParameters());
        draft.setRequest(evidence.getFullRequest());
        draft.setResponse(evidence.getFullResponse());

        VulnerabilityDialog.Result result = VulnerabilityDialog.show(rootPanel, this, draft, evidence);
        if (!result.isConfirmed()) {
            return;
        }
        markAsVulnerabilityDraftInBurp(selectedMessage);

        setBusy(true);
        backgroundExecutor.submit(() -> {
            try {
                String issueId = apiClient.createWebVulnerability(apiKey, draft);
                markAsVulnerabilityCreatedInBurp(selectedMessage, issueId);

                uploadDefaultScreenshot(apiKey, issueId, evidence);
                for (File file : result.getExtraAttachments()) {
                    uploadAttachmentFile(apiKey, issueId, file);
                }

                SwingUtilities.invokeLater(() -> registerCreatedVulnerability(projectId, draft, issueId));
                appendOutput("[+] Vulnerability successfully sent to project " + projectId + " (issue " + issueId + ").");
                SwingUtilities.invokeLater(() -> mainTabs.setSelectedIndex(0));
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Error creating vulnerability: " + ex.getMessage());
                showMessage("Failed to create vulnerability: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    private void uploadDefaultScreenshot(String apiKey, String issueId, HttpEvidence evidence) {
        try {
            byte[] png = EvidenceScreenshotRenderer.renderRequestResponsePng(
                evidence.getMethod(), evidence.getUrl(), evidence.getFullRequest(), evidence.getFullResponse()
            );
            apiClient.uploadAttachment(apiKey, currentCompanyId(), issueId, "Evidence01-" + issueId + ".png", "image/png", png);
        } catch (IOException | ConvisoApiException ex) {
            appendOutput("[!] Failed to attach automatic evidence: " + ex.getMessage());
        }
    }

    private void uploadAttachmentFile(String apiKey, String issueId, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String contentType = safe(Files.probeContentType(file.toPath()));
            apiClient.uploadAttachment(apiKey, currentCompanyId(), issueId, file.getName(),
                contentType.isEmpty() ? "application/octet-stream" : contentType, bytes);
        } catch (IOException | ConvisoApiException ex) {
            appendOutput("[!] Failed to attach file " + file.getName() + ": " + ex.getMessage());
        }
    }

    public List<AssetItem> currentProjectAssets() {
        ProjectItem project = settingsTab.getSelectedProject();
        return project == null ? new ArrayList<>() : project.getAssets();
    }

    public void fetchVulnerabilityTemplatesAsync(Consumer<List<VulnerabilityTemplateSummary>> onSuccess, Consumer<String> onError) {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            onError.accept("Enter the API Key in Settings > Configuration.");
            return;
        }
        String companyId = currentCompanyId();
        backgroundExecutor.submit(() -> {
            try {
                onSuccess.accept(apiClient.fetchVulnerabilityTemplates(apiKey, companyId, ""));
            } catch (ConvisoApiException ex) {
                onError.accept(ex.getMessage());
            }
        });
    }

    public void fetchVulnerabilityTemplateDetailAsync(String templateId, Consumer<VulnerabilityTemplateDetail> onSuccess, Consumer<String> onError) {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            onError.accept("Enter the API Key in Settings > Configuration.");
            return;
        }
        backgroundExecutor.submit(() -> {
            try {
                onSuccess.accept(apiClient.fetchVulnerabilityTemplateDetail(apiKey, templateId));
            } catch (ConvisoApiException ex) {
                onError.accept(ex.getMessage());
            }
        });
    }

    public void generateVulnerabilityAiFieldAsync(
        String field, HttpEvidence evidence, VulnerabilityTemplateDetail template, Consumer<String> onSuccess, Consumer<String> onError
    ) {
        String aiApiKey = settingsTab.readAiApiKey();
        if (aiApiKey.isEmpty()) {
            onError.accept("Set the AI API Key in Settings > Configuration.");
            return;
        }
        String providerId = settingsTab.currentAiProviderId();
        String language = settingsTab.currentSummaryLanguage();
        backgroundExecutor.submit(() -> {
            try {
                onSuccess.accept(aiService.generateVulnerabilityField(field, aiApiKey, providerId, language, evidence, template));
            } catch (AiServiceException ex) {
                onError.accept(ex.getMessage());
            }
        });
    }

    private void markAsVulnerabilityCreatedInBurp(IHttpRequestResponse message, String issueId) {
        markInBurp(message, "red", "Created Vulnerability (Issue " + issueId + ")");
    }

    private void markAsVulnerabilityDraftInBurp(IHttpRequestResponse message) {
        markInBurp(message, "gray", "Vulnerability in Draft");
    }

    private void registerCreatedVulnerability(String projectId, VulnerabilityDraft draft, String issueId) {
        VulnerabilityRecord record = new VulnerabilityRecord();
        record.setId(safe(issueId));
        record.setProjectId(safe(projectId));
        record.setTitle(safe(draft.getTitle()));
        record.setSeverity(safe(draft.getSeverity()));
        record.setCategory(safe(draft.getCategory()));
        record.setPattern(String.join(", ", draft.getPatterns()));
        record.setMethod(safe(draft.getMethod()));
        record.setEndpoint(safe(draft.getUrl()));
        record.setDescription(safe(draft.getDescription()));
        record.setJustification(safe(draft.getStepsToReproduce()));
        record.setEvidence(safe(draft.getSummary()));
        record.setStatus(safe(draft.getStatus()));
        record.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        vulnerabilitiesTab.addOrMergeRecord(record);
        vulnerabilitiesTab.selectFirst();
        vulnerabilitiesTab.refreshStatus();
        saveVulnerabilityItems();
    }

    private void classifyByTemplate(VulnerabilityRecord record) {
        String[] classification = VulnerabilityClassifier.inferCategoryAndPattern(record.getTemplate());
        if (record.getCategory() == null || record.getCategory().trim().isEmpty()) {
            record.setCategory(classification[0]);
        }
        if (record.getPattern() == null || record.getPattern().trim().isEmpty()) {
            record.setPattern(classification[1]);
        }
    }

    // ------------------------------------------------------------------
    // X9 item persistence
    // ------------------------------------------------------------------

    private void upsertX9Item(String projectId, String requirementId, String summary, IHttpRequestResponse message) {
        String key = X9Tab.keyOf(projectId, requirementId);
        X9Item existing = x9Tab.findByKey(key);
        if (existing != null) {
            existing.setSummary(summary);
            existing.setState("DRAFT");
            existing.setSentAt("");
            existing.setApprovedBy("");
            x9Tab.notifyItemChanged(existing);
            if (message != null) {
                x9MessageRefs.put(key, message);
            }
            saveX9Items();
            return;
        }

        X9Item item = new X9Item();
        item.setProjectId(projectId);
        item.setRequirementId(requirementId);
        item.setEntryId(key);
        item.setSummary(summary);
        item.setState("DRAFT");
        item.setTitle(requirementTitleOf(requirementId));

        x9Tab.addItem(item);
        if (message != null) {
            x9MessageRefs.put(key, message);
        }
        saveX9Items();
    }

    private void addX9DraftItem(String projectId, String requirementId, String summary, IHttpRequestResponse message) {
        X9Item item = new X9Item();
        item.setProjectId(projectId);
        item.setRequirementId(requirementId);
        item.setEntryId(X9Tab.keyOf(projectId, requirementId) + "::" + System.nanoTime());
        item.setSummary(summary);
        item.setState("DRAFT");
        item.setTitle(requirementTitleOf(requirementId));

        x9Tab.addItem(item);
        if (message != null) {
            x9MessageRefs.put(X9Tab.keyOf(item), message);
        }
        saveX9Items();
    }

    private void addVulnerabilityX9DraftItem(String projectId, String requirementId, String summary, VulnerabilityRecord record) {
        X9Item item = new X9Item();
        item.setProjectId(projectId);
        item.setRequirementId(requirementId);
        item.setEntryId(X9Tab.keyOf(projectId, requirementId) + "::" + System.nanoTime());
        item.setSummary(summary);
        item.setState("DRAFT");
        item.setTitle(requirementTitleOf(requirementId));

        x9Tab.addItem(item);
        x9VulnerabilityRefs.put(X9Tab.keyOf(item), record);
        saveX9Items();
    }

    private String requirementTitleOf(String requirementId) {
        RequirementItem requirement = findRequirementById(requirementId);
        return requirement == null ? "" : requirement.getTitle();
    }

    private void saveX9Items() {
        settings.setX9ItemsJson(gson.toJson(x9Tab.allItems()));
    }

    private void loadX9ItemsFromSettings() {
        String payload = settings.getX9ItemsJson();
        if (payload.trim().isEmpty()) {
            return;
        }
        try {
            X9Item[] items = gson.fromJson(payload, X9Item[].class);
            if (items != null) {
                x9Tab.loadItemsSilently(java.util.Arrays.asList(items));
            }
        } catch (RuntimeException ex) {
            logger.info("[!] Ignoring malformed local X9 payload: " + ex.getMessage());
        }
    }

    private void saveVulnerabilityItems() {
        settings.setVulnerabilityItemsJson(gson.toJson(vulnerabilitiesTab.allItems()));
    }

    private void loadVulnerabilityItemsFromSettings() {
        String payload = settings.getVulnerabilityItemsJson();
        if (payload.trim().isEmpty()) {
            vulnerabilitiesTab.refreshStatus();
            return;
        }
        try {
            VulnerabilityRecord[] items = gson.fromJson(payload, VulnerabilityRecord[].class);
            if (items != null) {
                for (VulnerabilityRecord item : items) {
                    vulnerabilitiesTab.addOrMergeRecord(item);
                }
            }
        } catch (RuntimeException ex) {
            logger.info("[!] Ignoring malformed local vulnerabilities payload: " + ex.getMessage());
        } finally {
            vulnerabilitiesTab.refreshStatus();
        }
    }

    // ------------------------------------------------------------------
    // Burp message helpers
    // ------------------------------------------------------------------

    private IHttpRequestResponse getFirstSelectedMessage(IContextMenuInvocation invocation) {
        IHttpRequestResponse[] selected = getSelectedMessages(invocation);
        return selected.length == 0 ? null : selected[0];
    }

    private IHttpRequestResponse[] getSelectedMessages(IContextMenuInvocation invocation) {
        if (invocation == null) {
            return new IHttpRequestResponse[0];
        }
        try {
            IHttpRequestResponse[] selected = invocation.getSelectedMessages();
            return selected == null ? new IHttpRequestResponse[0] : selected;
        } catch (RuntimeException ex) {
            return new IHttpRequestResponse[0];
        }
    }

    private void markAsSentInBurp(IHttpRequestResponse message, String requirementId) {
        markInBurp(message, "blue", "Requirement Evidence Submitted (Req " + requirementId + ")");
    }

    private void markAsRequirementDraftInBurp(IHttpRequestResponse message) {
        markInBurp(message, "gray", "Requirement in Draft");
    }

    private void markInBurp(IHttpRequestResponse message, String highlight, String note) {
        if (message == null) {
            return;
        }
        try {
            message.setHighlight(highlight);
            String currentComment = message.getComment();
            if (currentComment == null || currentComment.trim().isEmpty()) {
                message.setComment(note);
            } else if (!currentComment.contains(note)) {
                message.setComment(currentComment + " | " + note);
            }
        } catch (RuntimeException ex) {
            // Highlight/comment failures should not break the calling flow.
        }
    }

    private String[] extractIdsFromSelectedRequest(IContextMenuInvocation invocation) {
        try {
            IHttpRequestResponse[] selected = invocation.getSelectedMessages();
            if (selected == null || selected.length == 0 || selected[0] == null || selected[0].getRequest() == null) {
                return new String[]{"", ""};
            }
            return extractIdsFromMessage(selected[0]);
        } catch (RuntimeException ex) {
            return new String[]{"", ""};
        }
    }

    private String[] extractIdsFromMessage(IHttpRequestResponse message) {
        String projectId = "";
        String requirementId = "";

        try {
            if (message == null || message.getRequest() == null) {
                return new String[]{projectId, requirementId};
            }
            byte[] request = message.getRequest();
            IRequestInfo info = helpers.analyzeRequest(message);
            int bodyOffset = info.getBodyOffset();
            if (bodyOffset < 0 || bodyOffset >= request.length) {
                return new String[]{projectId, requirementId};
            }

            String body = helpers.bytesToString(slice(request, bodyOffset, request.length));
            if (body == null || body.trim().isEmpty()) {
                return new String[]{projectId, requirementId};
            }

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject variables = root.has("variables") && root.get("variables").isJsonObject()
                ? root.getAsJsonObject("variables") : null;
            JsonObject params = variables != null && variables.has("params") && variables.get("params").isJsonObject()
                ? variables.getAsJsonObject("params") : null;

            if (params != null) {
                if (params.has("projectId") && !params.get("projectId").isJsonNull()) {
                    projectId = params.get("projectId").getAsString();
                }
                if (params.has("projectRequirementId") && !params.get("projectRequirementId").isJsonNull()) {
                    requirementId = params.get("projectRequirementId").getAsString();
                }
            }
        } catch (RuntimeException ex) {
            // Ignore parse failures to avoid breaking the organize flow.
        }
        return new String[]{projectId, requirementId};
    }

    private static byte[] slice(byte[] input, int start, int endExclusive) {
        int len = Math.max(0, endExclusive - start);
        byte[] out = new byte[len];
        System.arraycopy(input, start, out, 0, len);
        return out;
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void setBusy(boolean busy) {
        SwingUtilities.invokeLater(() -> {
            for (AbstractButton button : busyButtons) {
                button.setEnabled(!busy);
            }
        });
    }

    private void showMessage(String text) {
        JOptionPane.showMessageDialog(rootPanel, text);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
