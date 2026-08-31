package com.ordevia.aidev.agent.tool;

public record ToolResult(boolean success, String output, String error) {
    public static ToolResult ok(String output) { return new ToolResult(true, output, null); }
    public static ToolResult fail(String error) { return new ToolResult(false, null, error); }
}
