package com.chrono.task.model;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Settings {
    private String jiraApiToken;
    private String jiraEmail;
    private String jiraBaseUrl = "";

    private String dataStoragePath = System.getProperty("user.home") + java.io.File.separator + ".chrono-task-ai";
    private long gitBackupInterval = 1;
    private ChronoUnit gitBackupUnit = ChronoUnit.HOURS;
    private boolean gitBackupEnabled = false;

    private long jiraRefreshInterval = 15;
    private ChronoUnit jiraRefreshUnit = ChronoUnit.MINUTES;
    private boolean jiraRefreshEnabled = false;
    private String jqlQuery = "";

    private String markdownFont = "System";
    // Application look: "DARK" or "LIGHT" (see ThemeManager).
    private String uiTheme = "DARK";

    // Terminal panel (Ctrl+F12). Blank values mean "library default".
    // Shells offered by the terminal panel's "+" menu; the first one is the default. Empty = system shell.
    private List<TerminalShell> terminalShells = new ArrayList<>();
    // Deprecated single shell, kept so old settings files still load; migrated into terminalShells on the fly.
    private String terminalShell = "";
    private String terminalStartDirectory = "";
    private String terminalTheme = "DARK";
    private String terminalFontFamily = "";
    private int terminalFontSize = 14;
    private int terminalScrollback = 5000;
    private boolean terminalCopyOnSelect = false;
    private boolean terminalCtrlCCopies = true;
    private boolean terminalCtrlVPastes = true;
}