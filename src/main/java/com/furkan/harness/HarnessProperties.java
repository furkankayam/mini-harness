package com.furkan.harness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness")
public record HarnessProperties(
        String workspace,
        int maxIterations,
        int keepRecentToolResults,
        int maxToolOutputChars,
        int commandTimeoutSeconds,
        boolean autoApprove) {
}
