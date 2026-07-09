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
    private static final int TEMPLATE_MIN_TOKEN_LENGTH = 4;

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

        JMenuItem createVuln = new JMenuItem("Create vuln");
        createVuln.addActionListener(e -> createVulnerabilityFromSelection(invocation));
        root.add(createVuln);

        JMenu requirementsMenu = new JMenu("Requirements");
        List<RequirementItem> catalog = requirementCatalog();
        if (!catalog.isEmpty()) {
            for (RequirementItem requirement : catalog) {
                JMenuItem requirementItem = new JMenuItem(requirement.toString());
                requirementItem.addActionListener(e -> stageInX9(invocation, requirement.getId()));
                requirementsMenu.add(requirementItem);
            }
        } else {
            JMenuItem empty = new JMenuItem("No requirements loaded");
            empty.setEnabled(false);
            requirementsMenu.add(empty);
        }
        root.add(requirementsMenu);

        List<JMenuItem> items = new ArrayList<>();
        items.add(root);
        return items;
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
        return projectId.isEmpty() ? "(nenhum projeto selecionado)" : projectId;
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
            showMessage("Informe a API Key.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Testando conexao com a API...");
        backgroundExecutor.submit(() -> {
            try {
                apiClient.fetchProjects(apiKey, settingsTab.currentCompanyId(), 1);
                persistSettings();
                appendOutput("[+] Conexao validada com sucesso.");
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Falha no teste: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void testAiConnection() {
        String aiApiKey = settingsTab.readAiApiKey();
        if (aiApiKey.isEmpty()) {
            showMessage("Informe a AI API Key.");
            return;
        }

        String provider = settingsTab.currentAiProviderId();
        setBusy(true);
        appendOutput("[+] Testando conexao com " + provider + "...");
        backgroundExecutor.submit(() -> {
            try {
                aiService.validateApiKey(aiApiKey, provider);
                persistSettings();
                appendOutput("[+] " + provider + " validada com sucesso.");
            } catch (AiServiceException ex) {
                appendOutput("[!] Falha no teste da IA (" + provider + "): " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void loadProjects() {
        String apiKey = settingsTab.readApiKey();
        String scopeId = settingsTab.currentCompanyId();

        if (apiKey.isEmpty()) {
            showMessage("Informe a API Key.");
            return;
        }
        if (scopeId.isEmpty()) {
            showMessage("Informe o Company ID.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Carregando projetos...");
        backgroundExecutor.submit(() -> {
            try {
                JsonArray projects = apiClient.fetchProjects(apiKey, scopeId, 100);
                SwingUtilities.invokeLater(() -> settingsTab.fillProjects(projects));
                persistSettings();
                appendOutput("[+] Projetos carregados: " + projects.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Erro ao carregar projetos: " + ex.getMessage());
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
    }

    // ------------------------------------------------------------------
    // Requirements tab actions
    // ------------------------------------------------------------------

    public void loadRequirements() {
        String apiKey = settingsTab.readApiKey();
        ProjectItem project = settingsTab.getSelectedProject();

        if (apiKey.isEmpty()) {
            showMessage("Informe a API Key.");
            return;
        }
        if (project == null) {
            showMessage("Selecione um projeto na aba Settings.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Carregando requirements do projeto " + project.getId() + "...");
        backgroundExecutor.submit(() -> {
            try {
                JsonArray requirements = apiClient.fetchRequirements(apiKey, project.getId());
                SwingUtilities.invokeLater(() -> requirementsTab.fillRequirements(requirements));
                appendOutput("[+] Requirements carregados: " + requirements.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Erro ao carregar requirements: " + ex.getMessage());
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
            showMessage("Informe a API Key.");
            return;
        }
        if (projectId.isEmpty()) {
            showMessage("Selecione um projeto em Settings > Configuration.");
            return;
        }

        setBusy(true);
        appendOutput("[+] Carregando vulnerabilidades do projeto " + projectId + "...");
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
                appendOutput("[+] Vulnerabilidades carregadas: " + vulnerabilities.size());
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Erro ao carregar vulnerabilidades: " + ex.getMessage());
                showMessage("Falha ao carregar vulnerabilidades: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    public void includeSelectedVulnerabilityInRequirements() {
        VulnerabilityRecord record = vulnerabilitiesTab.getSelectedRecord();
        if (record == null) {
            showMessage("Selecione uma vulnerabilidade.");
            return;
        }

        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Configure a API Key em Settings > Configuration.");
            return;
        }

        String projectId = currentProjectId();
        if (projectId.isEmpty()) {
            showMessage("Selecione um projeto em Settings > Configuration.");
            return;
        }

        if (requirementCatalog().isEmpty()) {
            try {
                JsonArray requirements = apiClient.fetchRequirements(apiKey, projectId);
                requirementsTab.fillRequirements(requirements);
            } catch (ConvisoApiException ex) {
                showMessage("Falha ao carregar requirements: " + ex.getMessage());
                return;
            }
        }

        setBusy(true);
        backgroundExecutor.submit(() -> {
            try {
                String requirementId = determineRequirementForVulnerability(record);
                if (requirementId.isEmpty()) {
                    throw new ConvisoApiException("Nao foi possivel identificar o requirement.");
                }
                if (!ensureRequirementRunning(requirementId)) {
                    return;
                }

                String summary = "Foi identificada a seguinte vulnerabilidade que consta no link: " + buildVulnerabilityLink(record);
                SwingUtilities.invokeLater(() -> {
                    settings.setRequirementId(requirementId);
                    requirementsTab.selectRequirementById(requirementId);
                });

                apiClient.markRequirementDone(apiKey, requirementId, summary, null);

                SwingUtilities.invokeLater(() -> mainTabs.setSelectedIndex(1));
                appendOutput("[+] Vulnerabilidade enviada diretamente para o requirement " + requirementId + " (status alterado para Done).");
            } catch (ConvisoApiException | AiServiceException ex) {
                appendOutput("[!] Erro ao incluir vulnerabilidade nos requirements: " + ex.getMessage());
                showMessage("Falha ao incluir vulnerabilidade: " + ex.getMessage());
            } finally {
                setBusy(false);
            }
        });
    }

    // ------------------------------------------------------------------
    // X9 tab actions
    // ------------------------------------------------------------------

    public void saveSelectedX9Summary() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Selecione um item no X9.");
            return;
        }
        item.setSummary(x9Tab.getSummaryText());
        x9Tab.notifyItemChanged(item);
        saveX9Items();
        x9Tab.setStatus("Resumo salvo para requirement " + item.getRequirementId() + ".");
    }

    public void refreshSelectedX9WithAi() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Selecione um item no X9.");
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
                    x9Tab.setStatus("Resumo atualizado com IA para requirement " + item.getRequirementId() + ".");
                });
            } finally {
                setBusy(false);
            }
        });
    }

    public void sendSelectedX9() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Selecione um item no X9.");
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
            showMessage("Nao ha itens pendentes no X9.");
            return;
        }
        for (X9Item item : pending) {
            publishX9Item(item);
        }
    }

    public void deleteSelectedX9Item() {
        X9Item item = x9Tab.getSelectedItem();
        if (item == null) {
            showMessage("Selecione um item no X9 para excluir.");
            return;
        }

        String state = safe(item.getState()).isEmpty() ? "DRAFT" : item.getState();
        int decision = JOptionPane.showConfirmDialog(
            rootPanel,
            "Excluir o requirement " + item.getRequirementId() + " do X9? Estado atual: " + state,
            "Confirmar exclusao",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (decision != JOptionPane.YES_OPTION) {
            return;
        }

        String key = X9Tab.keyOf(item);
        x9Tab.removeItemByKey(key);
        x9MessageRefs.remove(key);
        saveX9Items();
        appendOutput("[+] Requirement " + item.getRequirementId() + " removido do X9.");
    }

    private void publishX9Item(X9Item item) {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Configure a API Key em Settings > Configuration.");
            return;
        }
        if (safe(item.getSummary()).trim().isEmpty()) {
            showMessage("Resumo vazio para requirement " + item.getRequirementId() + ".");
            return;
        }
        if (!ensureRequirementRunning(item.getRequirementId())) {
            return;
        }

        IHttpRequestResponse message = x9MessageRefs.get(X9Tab.keyOf(item));
        if (message == null) {
            showMessage("Sem evidencia de request/response disponivel para o requirement " + item.getRequirementId()
                + " (referencia da mensagem original foi perdida, provavelmente por reinicio do Burp).");
            return;
        }

        boolean markAsDone = item.isMarkAsDone();

        setBusy(true);
        backgroundExecutor.submit(() -> {
            try {
                HttpEvidence evidence = evidenceExtractor.extract(message);
                byte[] evidencePng = EvidenceScreenshotRenderer.renderRequestResponsePng(
                    evidence.getMethod(), evidence.getUrl(), evidence.getFullRequest(), evidence.getFullResponse()
                );

                if (markAsDone) {
                    apiClient.markRequirementDone(apiKey, item.getRequirementId(), item.getSummary(), evidencePng);
                } else {
                    apiClient.addRequirementAttachment(apiKey, item.getRequirementId(), item.getSummary(), evidencePng);
                }

                item.setState("SENT");
                item.setSentAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                item.setApprovedBy(System.getProperty("user.name", "analyst"));
                SwingUtilities.invokeLater(() -> x9Tab.notifyItemChanged(item));

                markAsSentInBurp(message, item.getRequirementId());
                appendOutput("[+] Requirement " + item.getRequirementId() + " enviado para plataforma via X9"
                    + (markAsDone ? " (status alterado para Done)." : " (status permanece Running)."));
            } catch (IOException | ConvisoApiException ex) {
                appendOutput("[!] Erro ao enviar requirement " + item.getRequirementId() + ": " + ex.getMessage());
            } finally {
                setBusy(false);
                saveX9Items();
            }
        });
    }

    /** The extension only allows attaching evidence while a requirement is Running (IN_PROGRESS) on the platform, matching the platform's own rule. */
    private boolean ensureRequirementRunning(String requirementId) {
        RequirementItem requirement = findRequirementById(requirementId);
        if (requirement == null) {
            showMessage("Requirement " + requirementId + " nao encontrado no catalogo carregado. Recarregue os requirements em Requirements > Load requirements.");
            return false;
        }
        if (!"IN_PROGRESS".equalsIgnoreCase(safe(requirement.getStatus()))) {
            showMessage("Requirement " + requirementId + " precisa estar em Running na Conviso Platform para receber evidencia (status atual: "
                + requirement.getStatus() + "). Altere o status na plataforma e recarregue os requirements.");
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
            showMessage("Selecione um projeto em Settings > Configuration.");
            return;
        }
        if (requirementId.isEmpty()) {
            showMessage("Selecione um requirement na aba Requirements.");
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

        appendOutput("[+] Requirement " + requirementId + " foi para o X9 como rascunho.");
        mainTabs.setSelectedIndex(2);
    }

    private void organizeSelectedTest(IContextMenuInvocation invocation) {
        IHttpRequestResponse[] selectedMessages = getSelectedMessages(invocation);
        if (selectedMessages.length == 0) {
            showMessage("Selecione uma ou mais mensagens no Proxy para organizar.");
            return;
        }

        String projectFromSettings = currentProjectId();
        if (projectFromSettings.isEmpty()) {
            showMessage("Selecione um projeto em Settings > Configuration.");
            return;
        }

        if (requirementCatalog().isEmpty()) {
            String apiKey = settingsTab.readApiKey();
            if (apiKey.isEmpty()) {
                showMessage("Configure a API Key e carregue os requirements antes de organizar.");
                return;
            }
            try {
                JsonArray requirements = apiClient.fetchRequirements(apiKey, projectFromSettings);
                requirementsTab.fillRequirements(requirements);
                appendOutput("[+] Requirements carregados automaticamente para o Organize.");
            } catch (ConvisoApiException ex) {
                showMessage("Falha ao carregar requirements automaticamente: " + ex.getMessage());
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
            showMessage("Nenhum item foi organizado. Verifique se os requirements estao carregados.");
            return;
        }

        settings.setProjectId(lastProjectId);
        settings.setRequirementId(lastRequirementId);

        appendOutput("[+] Organize concluido: " + organizedCount + " item(ns) organizado(s), " + skippedCount + " ignorado(s).");
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
                appendOutput("[!] Classificacao por IA indisponivel, usando fallback: " + ex.getMessage());
            }
        }

        String evidenceText = evidenceExtractor.extract(message).asSearchableText();
        Optional<String> matched = RequirementMatcher.matchByDescriptionTokens(evidenceText, catalog, STANDARD_MIN_TOKEN_LENGTH);
        return matched.orElseGet(() -> RequirementMatcher.firstAvailable(catalog));
    }

    private void createVulnerabilityFromSelection(IContextMenuInvocation invocation) {
        IHttpRequestResponse selectedMessage = getFirstSelectedMessage(invocation);
        if (selectedMessage == null) {
            showMessage("Selecione uma mensagem no Proxy para criar vulnerabilidade.");
            return;
        }

        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            showMessage("Configure a API Key em Settings > Configuration.");
            return;
        }

        String projectId = currentProjectId();
        if (projectId.isEmpty()) {
            showMessage("Selecione um projeto em Settings > Configuration.");
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
                appendOutput("[+] Vulnerability enviada com sucesso para o projeto " + projectId + " (issue " + issueId + ").");
                SwingUtilities.invokeLater(() -> mainTabs.setSelectedIndex(0));
            } catch (ConvisoApiException ex) {
                appendOutput("[!] Erro ao criar vulnerabilidade: " + ex.getMessage());
                showMessage("Falha ao criar vulnerabilidade: " + ex.getMessage());
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
            apiClient.uploadAttachment(apiKey, currentCompanyId(), issueId, "evidence.png", "image/png", png);
        } catch (IOException | ConvisoApiException ex) {
            appendOutput("[!] Falha ao anexar evidencia automatica: " + ex.getMessage());
        }
    }

    private void uploadAttachmentFile(String apiKey, String issueId, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String contentType = safe(Files.probeContentType(file.toPath()));
            apiClient.uploadAttachment(apiKey, currentCompanyId(), issueId, file.getName(),
                contentType.isEmpty() ? "application/octet-stream" : contentType, bytes);
        } catch (IOException | ConvisoApiException ex) {
            appendOutput("[!] Falha ao anexar arquivo " + file.getName() + ": " + ex.getMessage());
        }
    }

    public List<AssetItem> currentProjectAssets() {
        ProjectItem project = settingsTab.getSelectedProject();
        return project == null ? new ArrayList<>() : project.getAssets();
    }

    public void fetchVulnerabilityTemplatesAsync(Consumer<List<VulnerabilityTemplateSummary>> onSuccess, Consumer<String> onError) {
        String apiKey = settingsTab.readApiKey();
        if (apiKey.isEmpty()) {
            onError.accept("Informe a API Key em Settings > Configuration.");
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
            onError.accept("Informe a API Key em Settings > Configuration.");
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
            onError.accept("Configure a AI API Key em Settings > Configuration.");
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
        record.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        vulnerabilitiesTab.addOrMergeRecord(record);
        vulnerabilitiesTab.selectFirst();
        vulnerabilitiesTab.refreshStatus();
        saveVulnerabilityItems();
    }

    // ------------------------------------------------------------------
    // Requirement matching for vulnerabilities
    // ------------------------------------------------------------------

    private String determineRequirementForVulnerability(VulnerabilityRecord record) throws AiServiceException {
        List<RequirementItem> catalog = requirementCatalog();
        if (record == null || catalog.isEmpty()) {
            return "";
        }

        String templateDriven = determineRequirementByTemplate(record, catalog);
        if (!templateDriven.isEmpty()) {
            return templateDriven;
        }

        String aiApiKey = settingsTab.readAiApiKey();
        if (aiApiKey.isEmpty()) {
            return fallbackRequirementForVulnerability(record, catalog);
        }

        String content = aiService.classifyRequirement(aiApiKey, settingsTab.currentAiProviderId(), null, catalog);
        String candidate = safe(content).trim().replaceAll("[^0-9A-Za-z_-]", "");
        if (!candidate.isEmpty() && findRequirementById(candidate) != null) {
            return candidate;
        }
        return fallbackRequirementForVulnerability(record, catalog);
    }

    private String determineRequirementByTemplate(VulnerabilityRecord record, List<RequirementItem> catalog) {
        String templateEvidence = (safe(record.getTemplate()) + " " + safe(record.getCategory()) + " " + safe(record.getPattern())).trim();
        if (templateEvidence.isEmpty()) {
            return "";
        }
        return RequirementMatcher.matchByQueryTokens(templateEvidence, catalog, TEMPLATE_MIN_TOKEN_LENGTH).orElse("");
    }

    private String fallbackRequirementForVulnerability(VulnerabilityRecord record, List<RequirementItem> catalog) {
        String evidence = safe(record.getTitle()) + " " + safe(record.getTemplate()) + " " + safe(record.getCategory()) + " "
            + safe(record.getPattern()) + " " + safe(record.getDescription()) + " " + safe(record.getJustification()) + " " + safe(record.getEvidence());
        return RequirementMatcher.matchByDescriptionTokens(evidence, catalog, STANDARD_MIN_TOKEN_LENGTH)
            .orElseGet(() -> RequirementMatcher.firstAvailable(catalog));
    }

    private String buildVulnerabilityLink(VulnerabilityRecord record) {
        if (record == null) {
            return "";
        }
        if (!safe(record.getEndpoint()).isEmpty()) {
            return safe(record.getEndpoint());
        }
        if (!safe(record.getId()).isEmpty()) {
            return "vulnerability/" + safe(record.getId());
        }
        return safe(record.getTitle());
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
            logger.info("[!] Ignorando payload local de X9 malformado: " + ex.getMessage());
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
            logger.info("[!] Ignorando payload local de vulnerabilidades malformado: " + ex.getMessage());
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
