package com.ordevia.aidev.security;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SecretRedactor {
    private static final String MASK="[REDACTED]";
    private static final List<Pattern> PATTERNS=List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._~+\\-/=]+"),
            Pattern.compile("(?i)((?:api[_-]?key|token|secret|password|passwd|pwd)\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bAKIA[A-Z0-9]{16}\\b")
    );

    public String redact(String value){
        if(value==null||value.isBlank())return value;String result=value;
        result=PATTERNS.get(0).matcher(result).replaceAll("$1"+MASK);
        result=PATTERNS.get(1).matcher(result).replaceAll("$1"+MASK);
        for(int i=2;i<PATTERNS.size();i++)result=PATTERNS.get(i).matcher(result).replaceAll(MASK);
        return result;
    }
}
