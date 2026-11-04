package com.chrono.task.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A shell the terminal panel can launch: a display name and a command line
 * (program + space separated arguments, e.g. {@code /bin/bash -l}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalShell {
    private String name = "";
    private String command = "";
}
