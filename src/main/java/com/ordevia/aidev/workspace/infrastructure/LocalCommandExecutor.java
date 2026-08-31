package com.ordevia.aidev.workspace.infrastructure;

import com.ordevia.aidev.workspace.application.CommandPolicy;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCommandExecutor {
    private final CommandPolicy policy;
    public LocalCommandExecutor(CommandPolicy policy){this.policy=policy;}
    public CommandResult execute(Path workspaceRoot,Path workingDirectory,List<String> command,Duration timeout){
        if(command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty"); policy.validate(command.getFirst()); Path root=workspaceRoot.toAbsolutePath().normalize(); Path cwd=workingDirectory.toAbsolutePath().normalize(); if(!cwd.startsWith(root)) throw new SecurityException("Working directory outside workspace root");
        try { Process process=new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start(); boolean finished=process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS); if(!finished){process.destroyForcibly();throw new IllegalStateException("Command timed out");} String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8); return new CommandResult(process.exitValue(),output); }
        catch(IOException e){throw new IllegalStateException("Unable to execute command",e);} catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Command interrupted",e);}
    }
    public record CommandResult(int exitCode,String output){}
}
